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
import { AuthService } from '../../../core/auth/auth.service';
import { Ticket, TicketMessage, TicketStatus } from '../../../core/tickets/ticket.models';
import { TicketService } from '../../../core/tickets/ticket.service';
import { TicketSummaryDialog } from '../../../shared/ticket-summary-dialog/ticket-summary-dialog';
import { TicketHistoryDialog } from '../../../shared/ticket-history-dialog/ticket-history-dialog';

const POLL_MS = 3000;
const LIST_POLL_MS = 5000;
const LAYOUT_STORAGE_KEY = 'admin-tickets-layout-v2';
const PAGE_SIZE = 25;
const GROUP_ORDER: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

type StatusFilter = TicketStatus | '';
/** all | unassigned | mine | admin:<id> */
type ScopeFilter = string;
type LayoutMode = 'split' | 'list' | 'chat';

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
    const rank = new Map(GROUP_ORDER.map((status, index) => [status, index]));
    return [...this.filteredTickets()].sort((a, b) => {
      const statusDiff = (rank.get(a.status) ?? 99) - (rank.get(b.status) ?? 99);
      if (statusDiff !== 0) {
        return statusDiff;
      }
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
    });
  });

  protected readonly listTotalPages = computed(() =>
    Math.max(1, Math.ceil(this.sortedTickets().length / PAGE_SIZE)),
  );

  protected readonly pagedTickets = computed(() => {
    const page = Math.min(this.listPage(), this.listTotalPages());
    const start = (page - 1) * PAGE_SIZE;
    return this.sortedTickets().slice(start, start + PAGE_SIZE);
  });

  protected readonly listRangeLabel = computed(() => {
    const total = this.sortedTickets().length;
    if (total === 0) {
      return '0 tickets';
    }
    const page = Math.min(this.listPage(), this.listTotalPages());
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

  protected goToListPage(page: number): void {
    const next = Math.min(Math.max(1, page), this.listTotalPages());
    this.listPage.set(next);
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
    if (this.listPollInFlight) {
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
      if (this.listPage() > this.listTotalPages()) {
        this.listPage.set(this.listTotalPages());
      }

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
