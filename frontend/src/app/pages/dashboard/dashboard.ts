import { DatePipe, NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
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
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { playMessageCue, unlockAudio } from '../../core/audio/cue-sounds';
import { AuthService } from '../../core/auth/auth.service';
import { ProfileService } from '../../core/profile/profile.service';
import {
  CreateTicketRequest,
  CsmRating,
  PendingCsm,
  Ticket,
  TicketCategoryOption,
  TicketMessage,
  TicketStatus,
} from '../../core/tickets/ticket.models';
import { TicketService } from '../../core/tickets/ticket.service';

const MAX_ID_BYTES = 5 * 1024 * 1024;
const MAX_ATTACHMENTS = 5;
const ALLOWED_ID_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'application/pdf']);
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const POLL_MS = 3000;
const LIST_POLL_MS = 5000;
const LAYOUT_STORAGE_KEY = 'dashboard-layout-v2';
const GROUP_ORDER: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

type LayoutMode = 'split' | 'list' | 'chat';

interface TicketGroup {
  status: TicketStatus;
  label: string;
  tickets: Ticket[];
  unread: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, DatePipe, NgClass],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit, OnDestroy {
  @ViewChild('messageEnd') private messageEnd?: ElementRef<HTMLElement>;

  protected readonly auth = inject(AuthService);
  private readonly ticketService = inject(TicketService);
  private readonly profileService = inject(ProfileService);

  protected readonly displayName = signal('');
  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly categories = signal<TicketCategoryOption[]>([]);
  protected readonly loadingTickets = signal(true);
  protected readonly loadError = signal<string | null>(null);

  protected readonly selectedTicketId = signal<number | null>(null);
  protected readonly messages = signal<TicketMessage[]>([]);
  protected readonly loadingMessages = signal(false);
  protected readonly messageError = signal<string | null>(null);
  protected readonly draft = signal('');
  protected readonly sendingMessage = signal(false);
  protected readonly live = signal(false);

  protected readonly showForm = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly category = signal('');
  protected readonly subject = signal('');
  protected readonly description = signal('');
  protected readonly idPhoto = signal<File | null>(null);
  protected readonly idPhotoName = signal('');
  protected readonly createAttachments = signal<File[]>([]);
  protected readonly draftAttachment = signal<File | null>(null);
  protected readonly draftAttachmentPreview = signal<string | null>(null);
  protected readonly attachmentUrls = signal<Record<number, string>>({});
  protected readonly attachmentLightboxUrl = signal<string | null>(null);
  protected readonly layoutMode = signal<LayoutMode>(this.readLayoutMode());
  protected readonly collapsedGroups = signal<Set<TicketStatus>>(
    new Set<TicketStatus>(['RESOLVED', 'CLOSED']),
  );

  protected readonly showCsm = signal(false);
  protected readonly pendingCsm = signal<PendingCsm | null>(null);
  protected readonly csmRating = signal<CsmRating | null>(null);
  protected readonly csmComment = signal('');
  protected readonly submittingCsm = signal(false);
  protected readonly csmError = signal<string | null>(null);

  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private listPollTimer: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;
  private listPollInFlight = false;
  private unreadPrimed = false;
  private knownOtherUnread = 0;

  protected readonly selectedTicket = computed(() => {
    const id = this.selectedTicketId();
    return this.tickets().find((t) => t.id === id) ?? null;
  });

  protected readonly ticketGroups = computed((): TicketGroup[] => {
    const list = this.tickets();
    return GROUP_ORDER.map((status) => {
      const tickets = list.filter((t) => t.status === status);
      return {
        status,
        label: this.statusLabel(status),
        tickets,
        unread: tickets.reduce((sum, t) => sum + (t.unreadCount ?? 0), 0),
      };
    });
  });

  protected readonly openCount = computed(
    () => this.tickets().filter((t) => t.status === 'OPEN').length,
  );
  protected readonly inProgressCount = computed(
    () => this.tickets().filter((t) => t.status === 'IN_PROGRESS').length,
  );

  async ngOnInit(): Promise<void> {
    this.displayName.set(this.auth.user()?.name ?? '');
    await Promise.all([this.loadCategories(), this.loadTickets(true), this.loadDirectoryName()]);
    await this.refreshPendingCsm(true);
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
  }

  @HostListener('document:visibilitychange')
  protected onVisibilityChange(): void {
    if (document.visibilityState === 'visible') {
      void this.loadTickets(false);
      if (this.selectedTicketId() != null) {
        void this.pollMessages();
      }
    }
  }

  protected signOut(): void {
    void this.auth.logout();
  }

  protected toggleGroup(status: TicketStatus): void {
    this.collapsedGroups.update((current) => {
      const next = new Set(current);
      if (next.has(status)) {
        next.delete(status);
      } else {
        next.add(status);
      }
      return next;
    });
  }

  protected isGroupCollapsed(status: TicketStatus): boolean {
    return this.collapsedGroups().has(status);
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
    this.live.set(false);
    this.clearUnreadLocally(ticket.id);
    await this.loadMessages(ticket.id, true);
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
  }

  protected async openForm(): Promise<void> {
    if (await this.refreshPendingCsm(true)) {
      return;
    }
    this.formError.set(null);
    this.category.set(this.categories()[0]?.value ?? '');
    this.subject.set('');
    this.description.set('');
    this.idPhoto.set(null);
    this.idPhotoName.set('');
    this.createAttachments.set([]);
    this.showForm.set(true);
  }

  protected selectCsmRating(rating: CsmRating): void {
    this.csmRating.set(rating);
    if (rating !== 'SAD') {
      this.csmComment.set('');
    }
    this.csmError.set(null);
  }

  protected async submitCsm(): Promise<void> {
    const pending = this.pendingCsm();
    const rating = this.csmRating();
    if (!pending || !rating || this.submittingCsm()) {
      return;
    }
    this.csmError.set(null);
    this.submittingCsm.set(true);
    try {
      await firstValueFrom(
        this.ticketService.submitCsm(pending.ticketId, {
          rating,
          comment: rating === 'SAD' ? this.csmComment().trim() || undefined : undefined,
        }),
      );
      this.csmRating.set(null);
      this.csmComment.set('');
      await this.refreshPendingCsm(true);
    } catch (err: unknown) {
      this.csmError.set(this.describeError(err));
    } finally {
      this.submittingCsm.set(false);
    }
  }

  protected closeForm(): void {
    this.showForm.set(false);
  }

  protected onIdSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.formError.set(null);
    if (!file) {
      this.idPhoto.set(null);
      this.idPhotoName.set('');
      return;
    }
    if (!ALLOWED_ID_TYPES.has(file.type)) {
      this.formError.set('ID photo must be JPG, PNG, WEBP, or PDF.');
      input.value = '';
      this.idPhoto.set(null);
      this.idPhotoName.set('');
      return;
    }
    if (file.size > MAX_ID_BYTES) {
      this.formError.set('ID photo must be 5 MB or smaller.');
      input.value = '';
      this.idPhoto.set(null);
      this.idPhotoName.set('');
      return;
    }
    this.idPhoto.set(file);
    this.idPhotoName.set(file.name);
  }

  protected onCreateAttachmentsSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const picked = Array.from(input.files ?? []);
    input.value = '';
    this.formError.set(null);
    if (picked.length === 0) {
      return;
    }
    const next = [...this.createAttachments()];
    for (const file of picked) {
      if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
        this.formError.set('Attachments must be JPG, PNG, or WEBP images.');
        return;
      }
      if (file.size > MAX_ID_BYTES) {
        this.formError.set('Each attachment must be 5 MB or smaller.');
        return;
      }
      if (next.length >= MAX_ATTACHMENTS) {
        this.formError.set(`You can attach up to ${MAX_ATTACHMENTS} pictures.`);
        break;
      }
      next.push(file);
    }
    this.createAttachments.set(next);
  }

  protected removeCreateAttachment(index: number): void {
    this.createAttachments.update((current) => current.filter((_, i) => i !== index));
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
    if (file.size > MAX_ID_BYTES) {
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

  protected async submitTicket(): Promise<void> {
    this.formError.set(null);
    const category = this.category();
    const subject = this.subject().trim();
    const description = this.description().trim();
    const idPhoto = this.idPhoto();
    const attachments = this.createAttachments();

    if (!category || !subject || !description) {
      this.formError.set('Please fill in category, subject, and description.');
      return;
    }
    if (!idPhoto) {
      this.formError.set('Please upload a picture of your ID.');
      return;
    }

    const request: CreateTicketRequest = { category, subject, description, idPhoto, attachments };
    this.submitting.set(true);
    try {
      const created = await firstValueFrom(this.ticketService.createTicket(request));
      this.tickets.update((current) => [created, ...current]);
      this.showForm.set(false);
      await this.selectTicket(created);
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 409) {
        this.showForm.set(false);
        await this.refreshPendingCsm(true);
        return;
      }
      this.formError.set(this.describeError(err));
    } finally {
      this.submitting.set(false);
    }
  }

  protected async sendMessage(): Promise<void> {
    const ticket = this.selectedTicket();
    const body = this.draft().trim();
    const attachment = this.draftAttachment();
    if (!ticket || this.sendingMessage() || ticket.status === 'CLOSED') {
      return;
    }
    if (!body && !attachment) {
      return;
    }
    this.sendingMessage.set(true);
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
    } catch (err: unknown) {
      this.messageError.set(this.describeError(err));
    } finally {
      this.sendingMessage.set(false);
    }
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
    return message.authorUserId === this.auth.userId()
      || message.authorEmail?.toLowerCase() === this.auth.user()?.email?.toLowerCase();
  }

  protected statusLabel(status: Ticket['status']): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'IN_PROGRESS':
        return 'In progress';
      case 'RESOLVED':
        return 'Resolved';
      case 'CLOSED':
        return 'Closed';
    }
  }

  protected statusDotClass(status: Ticket['status']): string {
    switch (status) {
      case 'OPEN':
        return 'bg-amber-500';
      case 'IN_PROGRESS':
        return 'bg-sky-500';
      case 'RESOLVED':
        return 'bg-emerald-500';
      case 'CLOSED':
        return 'bg-zinc-400';
    }
  }

  private async loadMessages(ticketId: number, showSpinner: boolean): Promise<void> {
    if (showSpinner) {
      this.loadingMessages.set(true);
      this.messageError.set(null);
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
    } catch (err: unknown) {
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

  private async pollMessages(): Promise<void> {
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
      // keep existing messages on background poll failure
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
          // leave missing; bubble can fall back to label
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
    this.pollTimer = setInterval(() => void this.pollMessages(), POLL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimer != null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
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

  private async loadDirectoryName(): Promise<void> {
    try {
      const profile = await firstValueFrom(this.profileService.getProfile());
      if (profile.name) {
        this.displayName.set(profile.name);
        this.auth.updateDisplayName(profile.name);
      }
    } catch {
      // keep session name
    }
  }

  private async loadCategories(): Promise<void> {
    try {
      this.categories.set(await firstValueFrom(this.ticketService.getCategories()));
    } catch {
      // non-fatal
    }
  }

  private async loadTickets(showSpinner = true): Promise<void> {
    if (this.listPollInFlight) {
      return;
    }
    this.listPollInFlight = true;
    if (showSpinner) {
      this.loadingTickets.set(true);
      this.loadError.set(null);
    }
    try {
      const tickets = await firstValueFrom(this.ticketService.getMyTickets());
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

      if (
        showSpinner
        && normalized.length > 0
        && selectedId == null
        && this.layoutMode() !== 'list'
        && window.matchMedia('(min-width: 1024px)').matches
      ) {
        await this.selectTicket(normalized[0]);
      }
    } catch (err: unknown) {
      if (showSpinner) {
        this.loadError.set(this.describeError(err));
      }
    } finally {
      this.listPollInFlight = false;
      if (showSpinner) {
        this.loadingTickets.set(false);
      }
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

  /** Returns true when a pending CSM modal was opened. */
  private async refreshPendingCsm(openModal: boolean): Promise<boolean> {
    try {
      const pending = await firstValueFrom(this.ticketService.getPendingCsm());
      this.pendingCsm.set(pending);
      if (pending && openModal) {
        this.showCsm.set(true);
        this.csmRating.set(null);
        this.csmComment.set('');
        this.csmError.set(null);
        return true;
      }
      if (!pending) {
        this.showCsm.set(false);
      }
      return false;
    } catch {
      return false;
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

  private describeError(err: unknown): string {
    if (err && typeof err === 'object' && 'error' in err) {
      const body = (err as { error?: unknown }).error;
      if (typeof body === 'string' && body.trim()) {
        return body;
      }
      if (body && typeof body === 'object' && 'message' in body && typeof (body as { message: unknown }).message === 'string') {
        return (body as { message: string }).message;
      }
    }
    return 'Something went wrong. Please try again.';
  }
}
