import { DatePipe, NgClass } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { playMessageCue, unlockAudio } from '../../../core/audio/cue-sounds';
import { AdminService } from '../../../core/admin/admin.service';
import { AdminSummary } from '../../../core/admin/admin.models';
import { AuthService, isAllowedUserEmail, allowedUserEmailLabel } from '../../../core/auth/auth.service';
import { Ticket, TicketMessage, TicketStatus, canEncodeLpuEmail, displayRequesterEmail, messageAuthorLabel as formatMessageAuthor, needsDirectoryLink } from '../../../core/tickets/ticket.models';
import { TicketService } from '../../../core/tickets/ticket.service';
import { DirectoryService } from '../../../core/directory/directory.service';
import { TicketSummaryDialog } from '../../../shared/ticket-summary-dialog/ticket-summary-dialog';
import { TicketHistoryDialog } from '../../../shared/ticket-history-dialog/ticket-history-dialog';

const POLL_MS = 3000;
const LIST_POLL_MS = 5000;
const LAYOUT_STORAGE_KEY = 'admin-tickets-layout-v3';
const PAGE_SIZE = 20;
const STATUS_RANK: Record<TicketStatus, number> = {
  OPEN: 0,
  IN_PROGRESS: 1,
  RESOLVED: 2,
  CLOSED: 3,
};
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

type StatusFilter = TicketStatus | '';
/** all | unassigned | mine | admin:<id> */
type ScopeFilter = string;
type LayoutMode = 'split' | 'list' | 'chat';
type SortKey =
  | 'ticketNumber'
  | 'subject'
  | 'categoryLabel'
  | 'requesterName'
  | 'requesterEmail'
  | 'assignedAdminName'
  | 'status'
  | 'channel'
  | 'updatedAt';
type SortDir = 'asc' | 'desc';

@Component({
  selector: 'app-admin-tickets',
  imports: [FormsModule, DatePipe, NgClass, RouterLink, TicketSummaryDialog, TicketHistoryDialog],
  templateUrl: './admin-tickets.html',
  host: {
    class: 'flex min-h-0 flex-1 flex-col overflow-hidden',
  },
})
export class AdminTickets implements OnInit, OnDestroy {
  @ViewChild('messageEnd') private messageEnd?: ElementRef<HTMLElement>;

  private readonly adminService = inject(AdminService);
  private readonly ticketService = inject(TicketService);
  private readonly directory = inject(DirectoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly assignees = signal<AdminSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly statusFilter = signal<StatusFilter>('');
  protected readonly scopeFilter = signal<ScopeFilter>('all');
  /** When true, scope is fixed to Mine (My tickets route). */
  protected readonly scopeLocked = signal(false);
  protected readonly layoutMode = signal<LayoutMode>(this.readLayoutMode());
  protected readonly listPage = signal(1);
  protected readonly sortKey = signal<SortKey>('updatedAt');
  protected readonly sortDir = signal<SortDir>('desc');
  protected readonly busyTicketIds = signal<Set<number>>(new Set());

  protected readonly selectedTicketId = signal<number | null>(null);
  protected readonly messages = signal<TicketMessage[]>([]);
  protected readonly loadingMessages = signal(false);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly messageError = signal<string | null>(null);
  protected readonly live = signal(false);
  protected readonly draftAttachment = signal<File | null>(null);
  protected readonly draftAttachmentPreview = signal<string | null>(null);
  protected readonly attachmentUrls = signal<Record<number, string>>({});
  protected readonly attachmentLightboxUrl = signal<string | null>(null);

  protected readonly idPhotoUrl = signal<string | null>(null);
  protected readonly idPhotoIsPdf = signal(false);
  protected readonly idPhotoLoading = signal(false);
  protected readonly idPhotoError = signal<string | null>(null);
  protected readonly idLightboxOpen = signal(false);
  protected readonly summaryTicket = signal<Ticket | null>(null);
  protected readonly historyTicket = signal<Ticket | null>(null);
  protected readonly encodeEmail = signal('');
  protected readonly encodingEmail = signal(false);
  protected readonly encodeError = signal<string | null>(null);
  protected readonly linkPersonType = signal('');
  protected readonly linkPersonNo = signal('');
  protected readonly linkingDirectory = signal(false);
  protected readonly linkDirectoryError = signal<string | null>(null);

  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private listPollTimer: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;
  private listPollInFlight = false;
  private unreadPrimed = false;
  private knownOtherUnread = 0;
  private ticketsReady = false;
  private pendingFocusTicket: Ticket | null = null;

  protected readonly filteredTickets = computed(() => {
    const scope = this.scopeFilter();
    const myId = this.auth.userId();
    const statusFilter = this.statusFilter();
    return this.tickets().filter((ticket) => {
      if (statusFilter && ticket.status !== statusFilter) {
        return false;
      }
      if (scope === 'mine') {
        return ticket.assignedAdminId != null && ticket.assignedAdminId === myId;
      }
      if (scope === 'unassigned') {
        return ticket.assignedAdminId == null;
      }
      if (scope.startsWith('admin:')) {
        const adminId = Number(scope.slice(6));
        return Number.isFinite(adminId) && ticket.assignedAdminId === adminId;
      }
      return true;
    });
  });

  protected readonly sortedTickets = computed(() => {
    const key = this.sortKey();
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    return [...this.filteredTickets()].sort((a, b) => this.compareTickets(a, b, key) * dir);
  });

  protected readonly listTotalPages = computed(() =>
    Math.max(1, Math.ceil(this.sortedTickets().length / PAGE_SIZE)),
  );

  protected readonly currentListPage = computed(() =>
    Math.min(this.listPage(), this.listTotalPages()),
  );

  protected readonly pagedTickets = computed(() => {
    const page = this.currentListPage();
    const start = (page - 1) * PAGE_SIZE;
    return this.sortedTickets().slice(start, start + PAGE_SIZE);
  });

  protected readonly listRangeLabel = computed(() => {
    const total = this.sortedTickets().length;
    if (total === 0) {
      return '0 shown';
    }
    const page = this.currentListPage();
    const start = (page - 1) * PAGE_SIZE + 1;
    const end = Math.min(page * PAGE_SIZE, total);
    return `${start}–${end} of ${total}`;
  });

  protected readonly selectedTicket = computed(() => {
    const id = this.selectedTicketId();
    if (id == null) {
      return null;
    }
    return this.filteredTickets().find((t) => t.id === id) ?? this.tickets().find((t) => t.id === id) ?? null;
  });

  protected readonly selectedTickets = computed(() => {
    const ticket = this.selectedTicket();
    return ticket ? [ticket] : [];
  });

  protected readonly statusOptions: { value: StatusFilter; label: string }[] = [
    { value: '', label: 'All statuses' },
    { value: 'OPEN', label: 'Open' },
    { value: 'IN_PROGRESS', label: 'In Progress' },
    { value: 'RESOLVED', label: 'Resolved' },
    { value: 'CLOSED', label: 'Closed' },
  ];

  protected readonly scopeOptions = computed(() => {
    const options: { value: string; label: string }[] = [
      { value: 'all', label: 'All' },
      { value: 'unassigned', label: 'Unassigned' },
    ];
    for (const admin of this.assignees()) {
      options.push({ value: `admin:${admin.id}`, label: admin.name });
    }
    return options;
  });

  constructor() {
    this.route.data.pipe(takeUntilDestroyed()).subscribe((data) => {
      const mine = data['scope'] === 'mine';
      this.scopeLocked.set(mine);
      this.scopeFilter.set(mine ? 'mine' : 'all');
      this.listPage.set(1);
      this.ensureSelectionVisible();
    });

    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      if (!this.ticketsReady) {
        return;
      }
      void this.focusTicketFromQuery(params.get('ticket'));
    });
  }

  async ngOnInit(): Promise<void> {
    const navState =
      typeof history !== 'undefined' ? (history.state as { focusTicket?: Ticket } | null) : null;
    this.pendingFocusTicket = navState?.focusTicket ?? null;

    await Promise.all([this.loadTickets(true), this.loadAssignees()]);
    this.ticketsReady = true;
    await this.applyPendingTicketFocus();
    this.listPollTimer = setInterval(() => void this.loadTickets(false), LIST_POLL_MS);
  }

  ngOnDestroy(): void {
    this.stopPolling();
    if (this.listPollTimer != null) {
      clearInterval(this.listPollTimer);
      this.listPollTimer = null;
    }
    this.clearDraftAttachment();
    this.revokeAttachmentUrls();
    this.revokeIdPhoto();
  }

  @HostListener('document:visibilitychange')
  protected onVisibilityChange(): void {
    if (document.visibilityState === 'visible') {
      void this.loadTickets(false);
      if (this.selectedTicketId() != null) {
        void this.pollMessages(false);
      }
    }
  }

  protected async onFilterChange(): Promise<void> {
    this.listPage.set(1);
    await this.loadTickets(true);
    this.ensureSelectionVisible();
  }

  protected onScopeChange(): void {
    this.listPage.set(1);
    this.ensureSelectionVisible();
  }

  protected toggleSort(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortKey.set(key);
      this.sortDir.set(key === 'updatedAt' ? 'desc' : 'asc');
    }
    this.listPage.set(1);
  }

  protected sortIcon(key: SortKey): string {
    if (this.sortKey() !== key) {
      return '↕';
    }
    return this.sortDir() === 'asc' ? '↑' : '↓';
  }

  protected goToListPage(page: number): void {
    this.listPage.set(Math.min(Math.max(1, page), this.listTotalPages()));
  }

  protected setLayoutMode(mode: LayoutMode): void {
    this.layoutMode.set(mode);
    try {
      sessionStorage.setItem(LAYOUT_STORAGE_KEY, mode);
    } catch {
      // ignore
    }
  }

  protected async selectTicket(ticket: Ticket): Promise<void> {
    if (this.layoutMode() === 'list') {
      this.setLayoutMode('split');
    }
    if (this.selectedTicketId() === ticket.id) {
      return;
    }
    unlockAudio();
    this.stopPolling();
    this.selectedTicketId.set(ticket.id);
    this.draft.set('');
    this.clearDraftAttachment();
    this.revokeAttachmentUrls();
    this.messageError.set(null);
    this.messages.set([]);
    this.clearUnreadLocally(ticket.id);
    this.resetDirectoryLinkForm();
    await Promise.all([this.loadMessages(ticket.id, true), this.loadIdPhoto(ticket)]);
    this.startPolling();
  }

  protected clearSelection(): void {
    this.stopPolling();
    this.selectedTicketId.set(null);
    this.messages.set([]);
    this.draft.set('');
    this.clearDraftAttachment();
    this.revokeAttachmentUrls();
    this.messageError.set(null);
    this.live.set(false);
    this.revokeIdPhoto();
    this.resetDirectoryLinkForm();
  }

  protected async sendMessage(): Promise<void> {
    const ticket = this.selectedTicket();
    const body = this.draft().trim();
    const attachment = this.draftAttachment();
    if (!ticket || this.sending() || ticket.status === 'CLOSED') {
      return;
    }
    if (!body && !attachment) {
      return;
    }
    this.sending.set(true);
    this.messageError.set(null);
    try {
      const created = await firstValueFrom(
        this.ticketService.postMessage(ticket.id, body, attachment),
      );
      this.mergeMessages([created]);
      this.draft.set('');
      this.clearDraftAttachment();
      await this.ensureAttachmentUrls([created]);
      queueMicrotask(() => this.scrollToBottom());
    } catch (err) {
      this.messageError.set(this.describeError(err));
    } finally {
      this.sending.set(false);
    }
  }

  protected onDraftAttachmentSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    this.messageError.set(null);
    if (!file) {
      return;
    }
    if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
      this.messageError.set('Pictures must be JPG, PNG, or WEBP.');
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      this.messageError.set('Picture must be 5 MB or smaller.');
      return;
    }
    this.clearDraftAttachment();
    this.draftAttachment.set(file);
    this.draftAttachmentPreview.set(URL.createObjectURL(file));
  }

  protected clearDraftAttachment(): void {
    const preview = this.draftAttachmentPreview();
    if (preview) {
      URL.revokeObjectURL(preview);
    }
    this.draftAttachment.set(null);
    this.draftAttachmentPreview.set(null);
  }

  protected attachmentUrl(messageId: number): string | null {
    return this.attachmentUrls()[messageId] ?? null;
  }

  protected openAttachmentLightbox(url: string): void {
    this.attachmentLightboxUrl.set(url);
  }

  protected closeAttachmentLightbox(): void {
    this.attachmentLightboxUrl.set(null);
  }

  protected onComposerKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
      return;
    }
    event.preventDefault();
    void this.sendMessage();
  }

  protected canMessage(ticket: Ticket | null = this.selectedTicket()): boolean {
    return ticket != null && ticket.status !== 'CLOSED';
  }

  protected isMine(message: TicketMessage): boolean {
    return message.authorUserId === this.auth.userId();
  }

  protected messageAuthorLabel(message: TicketMessage): string {
    const ticket = this.selectedTicket();
    return formatMessageAuthor(message, {
      isMine: this.isMine(message),
      requesterName: ticket?.requesterName,
    });
  }

  protected openIdLightbox(): void {
    if (this.idPhotoUrl() && !this.idPhotoIsPdf()) {
      this.idLightboxOpen.set(true);
    }
  }

  protected closeIdLightbox(): void {
    this.idLightboxOpen.set(false);
  }

  protected openSummary(ticket: Ticket, event?: Event): void {
    event?.stopPropagation();
    this.summaryTicket.set(ticket);
  }

  protected closeSummary(): void {
    this.summaryTicket.set(null);
  }

  protected async onDirectoryLinked(): Promise<void> {
    this.closeSummary();
    await this.loadTickets(true);
    await this.reloadSelectedMessages();
  }

  protected openHistory(ticket: Ticket, event?: Event): void {
    event?.stopPropagation();
    this.summaryTicket.set(null);
    this.historyTicket.set(ticket);
  }

  protected closeHistory(): void {
    this.historyTicket.set(null);
  }

  protected async onHistoryTicketSelected(ticket: Ticket): Promise<void> {
    this.closeHistory();
    await this.focusTicket(ticket);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ticket: ticket.id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private async applyPendingTicketFocus(): Promise<void> {
    if (this.pendingFocusTicket) {
      const ticket = this.pendingFocusTicket;
      this.pendingFocusTicket = null;
      await this.focusTicket(ticket);
      return;
    }
    await this.focusTicketFromQuery(this.route.snapshot.queryParamMap.get('ticket'));
  }

  private async focusTicketFromQuery(rawId: string | null): Promise<void> {
    if (!rawId) {
      return;
    }
    const id = Number(rawId);
    if (!Number.isFinite(id) || id <= 0) {
      return;
    }
    if (this.selectedTicketId() === id) {
      return;
    }
    const existing = this.tickets().find((t) => t.id === id);
    if (existing) {
      await this.focusTicket(existing);
      return;
    }
    // Tickets may be outside current filter response — still open conversation shell once listed.
    this.statusFilter.set('');
    if (!this.scopeLocked()) {
      this.scopeFilter.set('all');
    }
    await this.loadTickets(false);
    const found = this.tickets().find((t) => t.id === id);
    if (found) {
      await this.focusTicket(found);
    }
  }

  private async focusTicket(ticket: Ticket): Promise<void> {
    this.statusFilter.set('');
    if (!this.scopeLocked()) {
      this.scopeFilter.set('all');
    }
    this.tickets.update((list) => {
      if (list.some((t) => t.id === ticket.id)) {
        return list.map((t) => (t.id === ticket.id ? ticket : t));
      }
      return [ticket, ...list];
    });
    this.listPage.set(1);
    await this.selectTicket(ticket);
  }

  private compareTickets(a: Ticket, b: Ticket, key: SortKey): number {
    switch (key) {
      case 'updatedAt':
        return new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime();
      case 'status':
        return STATUS_RANK[a.status] - STATUS_RANK[b.status];
      case 'assignedAdminName': {
        const av = (a.assignedAdminName ?? '').toLowerCase();
        const bv = (b.assignedAdminName ?? '').toLowerCase();
        if (!av && bv) return 1;
        if (av && !bv) return -1;
        return av.localeCompare(bv);
      }
      case 'channel':
        return a.channel.localeCompare(b.channel);
      default: {
        const av = (a[key] ?? '').toString().toLowerCase();
        const bv = (b[key] ?? '').toString().toLowerCase();
        return av.localeCompare(bv);
      }
    }
  }

  protected async onAssign(ticket: Ticket, adminIdRaw: string): Promise<void> {
    const adminId = adminIdRaw ? Number(adminIdRaw) : null;
    this.setBusy(ticket.id, true);
    try {
      const updated = await firstValueFrom(this.adminService.assignTicket(ticket.id, adminId));
      this.replaceTicket(updated);
      this.ensureSelectionVisible();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticket.id, false);
    }
  }

  protected async onStatusChange(ticket: Ticket, status: TicketStatus): Promise<void> {
    this.setBusy(ticket.id, true);
    try {
      // Backend auto-assigns unassigned OPEN tickets to the acting admin when leaving OPEN.
      const updated = await firstValueFrom(this.adminService.updateTicketStatus(ticket.id, status));
      this.replaceTicket(updated);
      this.ensureSelectionVisible();
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.setBusy(ticket.id, false);
    }
  }

  protected isBusy(ticketId: number): boolean {
    return this.busyTicketIds().has(ticketId);
  }

  protected statusBadgeClass(status: TicketStatus): string {
    switch (status) {
      case 'OPEN':
        return 'bg-amber-50 text-amber-800 ring-1 ring-inset ring-amber-200';
      case 'IN_PROGRESS':
        return 'bg-sky-50 text-sky-800 ring-1 ring-inset ring-sky-200';
      case 'RESOLVED':
        return 'bg-emerald-50 text-emerald-800 ring-1 ring-inset ring-emerald-200';
      case 'CLOSED':
        return 'bg-zinc-100 text-zinc-600 ring-1 ring-inset ring-zinc-200';
    }
  }

  protected statusLabel(status: TicketStatus): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'IN_PROGRESS':
        return 'In Progress';
      case 'RESOLVED':
        return 'Resolved';
      case 'CLOSED':
        return 'Closed';
    }
  }

  protected channelLabel(channel: Ticket['channel']): string {
    return channel === 'ONSITE_RFID' ? 'Onsite' : 'Online';
  }

  protected requesterEmailLabel(ticket: Ticket): string {
    return displayRequesterEmail(ticket.requesterEmail, ticket.pendingEmail);
  }

  protected canEncode(ticket: Ticket): boolean {
    return canEncodeLpuEmail(ticket);
  }

  protected canLinkDirectory(ticket: Ticket): boolean {
    return needsDirectoryLink(ticket);
  }

  private resetDirectoryLinkForm(): void {
    this.linkPersonType.set('');
    this.linkPersonNo.set('');
    this.linkDirectoryError.set(null);
    this.linkingDirectory.set(false);
  }

  protected async onLinkDirectory(ticket: Ticket): Promise<void> {
    this.linkDirectoryError.set(null);
    const personNo = this.linkPersonNo().trim();
    if (!personNo) {
      this.linkDirectoryError.set('Enter a student or employee number.');
      return;
    }
    const email = ticket.requesterEmail?.trim().toLowerCase() ?? '';
    if (!isAllowedUserEmail(email)) {
      this.linkDirectoryError.set(`This ticket does not have an ${allowedUserEmailLabel()} address to encode.`);
      return;
    }

    this.linkingDirectory.set(true);
    try {
      const personType = this.linkPersonType().trim().toUpperCase();
      const result = await firstValueFrom(
        this.directory.encodeLpuEmail({
          email,
          ticketId: ticket.id,
          personType: personType || null,
          personNo,
        }),
      );
      this.tickets.update((current) =>
        current.map((row) => {
          const sameTicket = row.id === ticket.id;
          const sameEmail = row.requesterEmail?.trim().toLowerCase() === email;
          if (!sameTicket && !sameEmail) {
            return row;
          }
          return {
            ...row,
            requesterPersonType: result.personType,
            requesterPersonNo: result.personNo,
            requesterName: result.name?.trim() || row.requesterName,
            directoryUnlinked: false,
          };
        }),
      );
      this.applyDirectoryNameToMessages(result.name);
      this.linkPersonNo.set('');
      this.linkPersonType.set('');
      this.linkDirectoryError.set(null);
      await this.loadTickets(true);
      await this.reloadSelectedMessages();
    } catch (err) {
      this.linkDirectoryError.set(this.describeError(err));
    } finally {
      this.linkingDirectory.set(false);
    }
  }

  protected async onEncodeEmail(ticket: Ticket): Promise<void> {
    this.encodeError.set(null);
    const email = this.encodeEmail().trim().toLowerCase();
    if (!isAllowedUserEmail(email)) {
      this.encodeError.set(`Use an ${allowedUserEmailLabel()} address.`);
      return;
    }

    this.encodingEmail.set(true);
    try {
      await firstValueFrom(
        this.directory.encodeLpuEmail({
          email,
          ticketId: ticket.id,
          personType: ticket.requesterPersonType,
          personNo: ticket.requesterPersonNo,
        }),
      );
      this.encodeEmail.set('');
      await this.loadTickets(true);
      await this.reloadSelectedMessages();
    } catch (err) {
      this.encodeError.set(this.describeError(err));
    } finally {
      this.encodingEmail.set(false);
    }
  }

  private ensureSelectionVisible(): void {
    const id = this.selectedTicketId();
    if (id != null && !this.filteredTickets().some((t) => t.id === id)) {
      this.clearSelection();
    }
  }

  private readLayoutMode(): LayoutMode {
    try {
      const stored = sessionStorage.getItem(LAYOUT_STORAGE_KEY);
      if (stored === 'split' || stored === 'list' || stored === 'chat') {
        return stored;
      }
    } catch {
      // ignore
    }
    return 'split';
  }

  private async reloadSelectedMessages(): Promise<void> {
    const id = this.selectedTicketId();
    if (id != null) {
      await this.loadMessages(id, false);
    }
  }

  private applyDirectoryNameToMessages(name: string | null | undefined): void {
    const directoryName = name?.trim();
    if (!directoryName) {
      return;
    }
    this.messages.update((list) =>
      list.map((message) =>
        (message.authorRole ?? '').toUpperCase() === 'USER'
          ? { ...message, authorName: directoryName }
          : message,
      ),
    );
  }

  private async loadMessages(ticketId: number, showSpinner: boolean): Promise<void> {
    if (showSpinner) {
      this.loadingMessages.set(true);
    }
    try {
      const messages = await firstValueFrom(this.ticketService.listMessages(ticketId));
      const previousCount = this.messages().length;
      this.messages.set(messages);
      await this.ensureAttachmentUrls(messages);
      this.live.set(true);
      if (showSpinner || messages.length > previousCount) {
        queueMicrotask(() => this.scrollToBottom());
      }
    } catch (err) {
      if (showSpinner) {
        this.messages.set([]);
        this.revokeAttachmentUrls();
        this.messageError.set(this.describeError(err));
      }
    } finally {
      if (showSpinner) {
        this.loadingMessages.set(false);
      }
    }
  }

  private async pollMessages(silent: boolean): Promise<void> {
    const ticketId = this.selectedTicketId();
    if (ticketId == null || this.pollInFlight || document.visibilityState === 'hidden') {
      return;
    }
    this.pollInFlight = true;
    try {
      const messages = await firstValueFrom(this.ticketService.listMessages(ticketId));
      if (this.selectedTicketId() !== ticketId) {
        return;
      }
      const previousIds = new Set(this.messages().map((m) => m.id));
      const newcomers = messages.filter((m) => !previousIds.has(m.id));
      const hasNewFromOther = newcomers.some((m) => !this.isMine(m));
      this.messages.set(messages);
      await this.ensureAttachmentUrls(messages);
      this.live.set(true);
      this.clearUnreadLocally(ticketId);
      if (hasNewFromOther) {
        playMessageCue();
        queueMicrotask(() => this.scrollToBottom());
      } else if (newcomers.length > 0) {
        queueMicrotask(() => this.scrollToBottom());
      }
    } catch {
      if (!silent) {
        // keep existing messages on background poll failure
      }
    } finally {
      this.pollInFlight = false;
    }
  }

  private async ensureAttachmentUrls(messages: TicketMessage[]): Promise<void> {
    const current = { ...this.attachmentUrls() };
    const needed = messages.filter((m) => m.hasAttachment && !current[m.id]);
    await Promise.all(
      needed.map(async (message) => {
        try {
          const blob = await firstValueFrom(
            this.ticketService.getMessageAttachment(message.ticketId, message.id),
          );
          current[message.id] = URL.createObjectURL(blob);
        } catch {
          // leave missing
        }
      }),
    );
    this.attachmentUrls.set(current);
  }

  private revokeAttachmentUrls(): void {
    for (const url of Object.values(this.attachmentUrls())) {
      URL.revokeObjectURL(url);
    }
    this.attachmentUrls.set({});
    this.attachmentLightboxUrl.set(null);
  }

  private startPolling(): void {
    this.stopPolling();
    this.pollTimer = setInterval(() => void this.pollMessages(true), POLL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimer != null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private async loadIdPhoto(ticket: Ticket): Promise<void> {
    this.revokeIdPhoto();
    this.idPhotoError.set(null);
    this.idLightboxOpen.set(false);
    if (!ticket.hasIdPhoto) {
      return;
    }
    this.idPhotoLoading.set(true);
    try {
      const blob = await firstValueFrom(this.ticketService.getIdPhoto(ticket.id));
      this.idPhotoIsPdf.set(blob.type === 'application/pdf' || blob.type.includes('pdf'));
      this.idPhotoUrl.set(URL.createObjectURL(blob));
    } catch {
      this.idPhotoError.set('Could not load ID photo.');
    } finally {
      this.idPhotoLoading.set(false);
    }
  }

  private revokeIdPhoto(): void {
    const url = this.idPhotoUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.idPhotoUrl.set(null);
    this.idPhotoIsPdf.set(false);
    this.idPhotoLoading.set(false);
    this.idPhotoError.set(null);
  }

  private mergeMessages(incoming: TicketMessage[]): void {
    this.messages.update((current) => {
      const ids = new Set(current.map((m) => m.id));
      const next = [...current];
      for (const message of incoming) {
        if (!ids.has(message.id)) {
          next.push(message);
        }
      }
      return next;
    });
  }

  private scrollToBottom(): void {
    this.messageEnd?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }

  private async loadTickets(showSpinner: boolean): Promise<void> {
    if (this.listPollInFlight && !showSpinner) {
      return;
    }
    this.listPollInFlight = true;
    if (showSpinner) {
      this.loading.set(true);
      this.error.set(null);
    }
    try {
      const tickets = await firstValueFrom(this.adminService.listTickets(this.statusFilter()));
      const selectedId = this.selectedTicketId();
      const normalized = tickets.map((t) =>
        selectedId != null && t.id === selectedId ? { ...t, unreadCount: 0 } : t,
      );
      this.tickets.set(normalized);

      const otherUnread = normalized
        .filter((t) => t.id !== selectedId)
        .reduce((sum, t) => sum + (t.unreadCount ?? 0), 0);
      if (this.unreadPrimed && otherUnread > this.knownOtherUnread) {
        unlockAudio();
        playMessageCue();
      }
      this.knownOtherUnread = otherUnread;
      this.unreadPrimed = true;

      this.ensureSelectionVisible();

      const visible = this.filteredTickets();
      if (
        showSpinner
        && this.selectedTicketId() == null
        && visible.length > 0
        && this.layoutMode() !== 'list'
        && window.matchMedia('(min-width: 1024px)').matches
      ) {
        await this.selectTicket(visible[0]);
      }
    } catch (err) {
      if (showSpinner) {
        this.error.set(this.describeError(err));
      }
    } finally {
      this.listPollInFlight = false;
      if (showSpinner) {
        this.loading.set(false);
      }
    }
  }

  private clearUnreadLocally(ticketId: number): void {
    this.tickets.update((current) =>
      current.map((t) => (t.id === ticketId ? { ...t, unreadCount: 0 } : t)),
    );
  }

  protected unreadLabel(count: number): string {
    return count > 99 ? '99+' : String(count);
  }

  private async loadAssignees(): Promise<void> {
    try {
      this.assignees.set(await firstValueFrom(this.adminService.listAssignees()));
    } catch {
      // Non-fatal
    }
  }

  private replaceTicket(updated: Ticket): void {
    this.tickets.update((current) => current.map((t) => (t.id === updated.id ? updated : t)));
  }

  private setBusy(ticketId: number, busy: boolean): void {
    this.busyTicketIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(ticketId);
      } else {
        next.delete(ticketId);
      }
      return next;
    });
  }

  private describeError(err: unknown): string {
    const message =
      err && typeof err === 'object' && 'error' in err
        ? ((err as { error?: { message?: string } }).error?.message ?? null)
        : null;
    return message ?? 'Something went wrong. Please try again.';
  }
}
