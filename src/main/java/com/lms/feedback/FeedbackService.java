package com.lms.feedback;

import java.security.SecureRandom;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.common.domain.AppUser;
import com.lms.common.domain.AppUserRepository;
import com.lms.common.domain.FeedbackCategory;
import com.lms.common.domain.FeedbackCategoryRepository;
import com.lms.common.domain.FeedbackHistory;
import com.lms.common.domain.FeedbackPriority;
import com.lms.common.domain.FeedbackStatus;
import com.lms.common.domain.Member;
import com.lms.common.domain.MemberFeedback;
import com.lms.common.domain.MemberFeedbackRepository;
import com.lms.common.domain.MemberRepository;
import com.lms.common.security.AuditAction;
import com.lms.common.web.SelectOption;
import com.lms.feedback.dto.FeedbackDetailView;
import com.lms.feedback.dto.FeedbackForm;
import com.lms.feedback.dto.FeedbackListRow;
import com.lms.feedback.dto.FeedbackReviewForm;
import com.lms.feedback.dto.FeedbackReviewView;
import com.lms.feedback.dto.HistoryEntryView;
import com.lms.feedback.dto.MyFeedbackRow;

/**
 * UC-10 — full CRUD on both sides. Create/Read/Update/"Delete" (withdraw,
 * never a hard delete) for the member who owns the feedback; Read/Update
 * (respond and move status) for staff, who can never delete at all.
 *
 * <p><b>Why {@link #submit} never writes a {@code FeedbackHistory} row.</b>
 * {@code database/01_schema.sql}'s {@code CK_FeedbackHistory_SomethingChanged}
 * requires {@code PreviousStatus <> NewStatus OR} the two response columns
 * to differ — confirmed live against the real database: a same-status,
 * both-responses-null row is rejected outright. A brand-new submission has
 * no real "previous" state to record a change from (it did not exist a
 * moment ago under some other status, and {@code PreviousStatus} is
 * {@code NOT NULL} — there is no "not yet submitted" value to put there),
 * so there is no constraint-satisfying row to write at creation. Every
 * *real* transition — a member withdrawing (Submitted → Closed), or staff
 * moving the status and/or writing a response — does write one; only the
 * very first moment is unrepresentable under this schema. Flagged here,
 * not silently worked around, exactly like the earlier submit-only pass
 * of this class already flagged it.
 *
 * <p><b>Ownership.</b> Every member-facing method below takes {@code
 * memberId} (resolved by the controller from the signed-in {@code
 * AppUser}, the same "does this AppUser have a Member row" lookup every
 * member-facing controller in this codebase uses) and filters by it here,
 * in the service — never trusting the URL's {@code feedbackId} alone. A
 * member probing another member's feedback id gets the same {@link
 * NoSuchElementException} as a genuinely stale id, so it learns nothing.
 */
@Service
@Transactional
public class FeedbackService {

    /** Rows per page for the staff feedback list. */
    static final int PAGE_SIZE = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberFeedbackRepository feedbacks;
    private final FeedbackCategoryRepository categories;
    private final MemberRepository members;
    private final AppUserRepository appUsers;

    public FeedbackService(MemberFeedbackRepository feedbacks, FeedbackCategoryRepository categories,
            MemberRepository members, AppUserRepository appUsers) {
        this.feedbacks = feedbacks;
        this.categories = categories;
        this.members = members;
        this.appUsers = appUsers;
    }

    // ---- Options shared by more than one screen ----

    @Transactional(readOnly = true)
    public List<SelectOption> categoryOptions() {
        return categories.findByActiveTrueOrderByCategoryName().stream()
                .map(c -> new SelectOption(c.getFeedbackCategoryId().toString(), c.getCategoryName()))
                .toList();
    }

    /** The staff list's category filter: every category, including retired ones old feedback may still reference, plus "All categories". */
    @Transactional(readOnly = true)
    public List<SelectOption> categoryFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All categories"));
        categories.findAllByOrderByCategoryName()
                .forEach(c -> options.add(new SelectOption(c.getFeedbackCategoryId().toString(), c.getCategoryName())));
        return options;
    }

    /** CK_MemberFeedback_Priority: Low, Medium, High — no reference table for this one, just the three values. */
    public static List<SelectOption> priorityOptions() {
        return List.of(
                new SelectOption("Low", "Low"),
                new SelectOption("Medium", "Medium"),
                new SelectOption("High", "High"));
    }

    public static List<SelectOption> priorityFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All priorities"));
        options.addAll(priorityOptions());
        return options;
    }

    /** CK_MemberFeedback_Status, in lifecycle order — business-rules §6. Values are the spaced dbValue text, e.g. "Under Review". */
    public static List<SelectOption> statusOptions() {
        return List.of(
                new SelectOption(FeedbackStatus.Submitted.dbValue(), FeedbackStatus.Submitted.dbValue()),
                new SelectOption(FeedbackStatus.UnderReview.dbValue(), FeedbackStatus.UnderReview.dbValue()),
                new SelectOption(FeedbackStatus.InProgress.dbValue(), FeedbackStatus.InProgress.dbValue()),
                new SelectOption(FeedbackStatus.Resolved.dbValue(), FeedbackStatus.Resolved.dbValue()),
                new SelectOption(FeedbackStatus.Closed.dbValue(), FeedbackStatus.Closed.dbValue()));
    }

    public static List<SelectOption> statusFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All statuses"));
        options.addAll(statusOptions());
        return options;
    }

    // ================= MEMBER SIDE =================

    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "CREATE", entity = "MemberFeedback")
    public MemberFeedback submit(Integer memberId, FeedbackForm form) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("Member not found"));
        FeedbackCategory category = categories.findById(form.getCategoryId())
                .orElseThrow(() -> new NoSuchElementException("Selected category no longer exists"));

        MemberFeedback feedback = new MemberFeedback();
        feedback.setFeedbackReference(generateUniqueReference());
        feedback.setMember(member);
        feedback.setCategory(category);
        feedback.setSubject(form.getSubject().trim());
        feedback.setDescription(form.getDescription().trim());
        feedback.setPriority(FeedbackPriority.valueOf(form.getPriority()));
        // Status stays at the entity's own default, Submitted. See class
        // javadoc for why no FeedbackHistory row is written here.
        return feedbacks.save(feedback);
    }

    /** "MEM-<year>-<6 random digits>" is RegistrationService's exact pattern, reused here for FB-. */
    private String generateUniqueReference() {
        String year = String.valueOf(Year.now().getValue());
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "FB-" + year + "-" + String.format("%06d", RANDOM.nextInt(1_000_000));
            if (!feedbacks.existsByFeedbackReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique feedback reference after 10 attempts");
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<MyFeedbackRow> myFeedback(Integer memberId) {
        return feedbacks.findByMemberMemberIdOrderBySubmittedAtDesc(memberId).stream()
                .map(f -> new MyFeedbackRow(f.getFeedbackId(), f.getFeedbackReference(), f.getCategory().getCategoryName(),
                        f.getSubject(), f.getStatus().dbValue(), f.getSubmittedAt()))
                .toList();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public FeedbackDetailView detail(Integer memberId, Integer feedbackId) {
        MemberFeedback feedback = ownedByMember(memberId, feedbackId);
        return toDetailView(feedback);
    }

    /** Populates {@link FeedbackForm} for the edit page (GET) — checks ownership and the editable condition before the member even sees a form. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public FeedbackForm forEdit(Integer memberId, Integer feedbackId) {
        MemberFeedback feedback = ownedByMember(memberId, feedbackId);
        requireEditable(feedback);
        FeedbackForm form = new FeedbackForm();
        form.setCategoryId(feedback.getCategory().getFeedbackCategoryId());
        form.setSubject(feedback.getSubject());
        form.setDescription(feedback.getDescription());
        form.setPriority(feedback.getPriority().name());
        return form;
    }

    /**
     * UC-10: "the member may edit subject, description, category and
     * priority ONLY while status is Submitted and AdminResponse is null."
     * A content-only edit changes neither {@code Status} nor {@code
     * AdminResponse}, so — same reasoning as {@link #submit} — there is no
     * {@code FeedbackHistory} row that could satisfy {@code
     * CK_FeedbackHistory_SomethingChanged} for it; {@code FeedbackHistory}
     * tracks status/response changes only, which is exactly what the
     * staff-side instructions for this table say ("recording previous and
     * new status, previous and new response").
     */
    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "UPDATE", entity = "MemberFeedback")
    public MemberFeedback edit(Integer memberId, Integer feedbackId, FeedbackForm form) {
        MemberFeedback feedback = ownedByMember(memberId, feedbackId);
        requireEditable(feedback);
        FeedbackCategory category = categories.findById(form.getCategoryId())
                .orElseThrow(() -> new NoSuchElementException("Selected category no longer exists"));
        feedback.setCategory(category);
        feedback.setSubject(form.getSubject().trim());
        feedback.setDescription(form.getDescription().trim());
        feedback.setPriority(FeedbackPriority.valueOf(form.getPriority()));
        return feedbacks.save(feedback);
    }

    /** UC-10 "Delete": withdraw. Same condition as edit. Never a hard delete — the record stays for reporting. */
    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "WITHDRAW", entity = "MemberFeedback")
    public MemberFeedback withdraw(Integer memberId, Integer feedbackId) {
        MemberFeedback feedback = ownedByMember(memberId, feedbackId);
        requireEditable(feedback);

        FeedbackStatus previousStatus = feedback.getStatus();
        feedback.setStatus(FeedbackStatus.Closed);
        feedbacks.save(feedback);

        FeedbackHistory history = new FeedbackHistory();
        history.setFeedback(feedback);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(FeedbackStatus.Closed);
        history.setPreviousResponse(feedback.getAdminResponse());
        history.setNewResponse(feedback.getAdminResponse());
        // The member withdrawing is their own AppUser — Member.user is
        // already loaded (ownedByMember touched it to reach memberId).
        history.setChangedBy(feedback.getMember().getUser());
        feedback.getHistory().add(history);

        return feedback;
    }

    private MemberFeedback ownedByMember(Integer memberId, Integer feedbackId) {
        return feedbacks.findById(feedbackId)
                .filter(f -> f.getMember().getMemberId().equals(memberId))
                .orElseThrow(() -> new NoSuchElementException("Feedback not found"));
    }

    /** UC-10's own words: "ONLY while status is Submitted and AdminResponse is null." Shared by edit and withdraw. */
    private static void requireEditable(MemberFeedback feedback) {
        if (feedback.getStatus() != FeedbackStatus.Submitted || feedback.getAdminResponse() != null) {
            throw new FeedbackException("This feedback can no longer be edited or withdrawn — staff have already responded to it.");
        }
    }

    // ================= STAFF SIDE =================
    // UC-10's own "Supporting Actor: Library Administrator" — see docs/scenarios.pdf.

    private static final String STAFF_ROLE = "hasAuthority('Library Administrator')";

    @PreAuthorize(STAFF_ROLE)
    @Transactional(readOnly = true)
    public Page<FeedbackListRow> staffList(String q, String status, Integer categoryId, String priority,
            int page, String sort, String dir) {
        String likeQuery = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        FeedbackStatus statusFilter = (status == null || status.isBlank()) ? null : parseStatus(status);
        FeedbackPriority priorityFilter = (priority == null || priority.isBlank()) ? null : FeedbackPriority.valueOf(priority);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sortFor(sort, dir));
        return feedbacks.search(likeQuery, statusFilter, categoryId, priorityFilter, pageable).map(this::toListRow);
    }

    /** Only real {@link MemberFeedback} columns are sortable — the submitting member's name is a join, not a column (same restraint as {@code BookService.sortFor}). */
    private static Sort sortFor(String sort, String dir) {
        String property = switch (sort == null ? "" : sort) {
            case "reference" -> "feedbackReference";
            case "subject" -> "subject";
            case "status" -> "status";
            case "priority" -> "priority";
            default -> "submittedAt";
        };
        return Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }

    private FeedbackListRow toListRow(MemberFeedback f) {
        String memberName = f.getMember().getUser().getFirstName() + " " + f.getMember().getUser().getLastName();
        return new FeedbackListRow(f.getFeedbackId(), f.getFeedbackReference(), memberName, f.getCategory().getCategoryName(),
                f.getSubject(), f.getStatus().dbValue(), f.getPriority().name(), f.getSubmittedAt());
    }

    @PreAuthorize(STAFF_ROLE)
    @Transactional(readOnly = true)
    public FeedbackReviewView staffDetail(Integer feedbackId) {
        MemberFeedback feedback = feedbacks.findById(feedbackId)
                .orElseThrow(() -> new NoSuchElementException("Feedback not found"));
        return toReviewView(feedback);
    }

    /**
     * UC-10: staff move the status through the lifecycle and write a
     * response, in one action. "Staff cannot skip backwards from Closed"
     * — Closed is the last status in the lifecycle, so "backwards from
     * Closed" and "any change at all once Closed" are the same rule;
     * enforced below before anything else is even parsed. Every other
     * status pair (including genuinely moving backwards, e.g. Resolved →
     * In Progress, to correct a mistake) is left open — UC-10 only names
     * the one restriction, so this does not invent a stricter forward-only
     * state machine it never asked for.
     */
    @PreAuthorize(STAFF_ROLE)
    @AuditAction(action = "UPDATE", entity = "MemberFeedback")
    public MemberFeedback review(Integer staffUserId, Integer feedbackId, FeedbackReviewForm form) {
        MemberFeedback feedback = feedbacks.findById(feedbackId)
                .orElseThrow(() -> new NoSuchElementException("Feedback not found"));
        if (feedback.getStatus() == FeedbackStatus.Closed) {
            throw new FeedbackException("This feedback is closed and can no longer be changed.");
        }

        FeedbackStatus newStatus = parseStatus(form.getStatus());
        String newResponse = (form.getResponse() == null || form.getResponse().isBlank()) ? null : form.getResponse().trim();

        FeedbackStatus previousStatus = feedback.getStatus();
        String previousResponse = feedback.getAdminResponse();
        boolean statusChanged = previousStatus != newStatus;
        boolean responseChanged = !Objects.equals(previousResponse, newResponse);
        if (!statusChanged && !responseChanged) {
            throw new FeedbackException("Nothing to update — change the status or the response first.");
        }

        feedback.setStatus(newStatus);
        feedback.setAdminResponse(newResponse);
        feedbacks.save(feedback);

        FeedbackHistory history = new FeedbackHistory();
        history.setFeedback(feedback);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setPreviousResponse(previousResponse);
        history.setNewResponse(newResponse);
        history.setChangedBy(appUsers.getReferenceById(staffUserId));
        feedback.getHistory().add(history);

        return feedback;
    }

    private static FeedbackStatus parseStatus(String dbValue) {
        for (FeedbackStatus candidate : FeedbackStatus.values()) {
            if (candidate.dbValue().equals(dbValue)) {
                return candidate;
            }
        }
        throw new FeedbackException("Unrecognised status.");
    }

    // ---- Shared view-building ----

    private FeedbackDetailView toDetailView(MemberFeedback f) {
        boolean editable = f.getStatus() == FeedbackStatus.Submitted && f.getAdminResponse() == null;
        return new FeedbackDetailView(f.getFeedbackId(), f.getFeedbackReference(), f.getCategory().getCategoryName(),
                f.getSubject(), f.getDescription(), f.getPriority().name(), f.getStatus().dbValue(), f.getAdminResponse(),
                f.getSubmittedAt(), f.getUpdatedAt(), editable, editable, historyOf(f));
    }

    private FeedbackReviewView toReviewView(MemberFeedback f) {
        String memberName = f.getMember().getUser().getFirstName() + " " + f.getMember().getUser().getLastName();
        return new FeedbackReviewView(f.getFeedbackId(), f.getFeedbackReference(), memberName, f.getMember().getMembershipNo(),
                f.getCategory().getCategoryName(), f.getSubject(), f.getDescription(), f.getPriority().name(),
                f.getStatus().dbValue(), f.getAdminResponse(), f.getSubmittedAt(), f.getUpdatedAt(),
                f.getStatus() == FeedbackStatus.Closed, historyOf(f));
    }

    private static List<HistoryEntryView> historyOf(MemberFeedback f) {
        return f.getHistory().stream()
                .map(h -> {
                    AppUser changedBy = h.getChangedBy();
                    String changedByName = changedBy.getFirstName() + " " + changedBy.getLastName();
                    boolean statusChanged = h.getPreviousStatus() != h.getNewStatus();
                    boolean responseChanged = !Objects.equals(h.getPreviousResponse(), h.getNewResponse());
                    return new HistoryEntryView(h.getChangedAt(), changedByName, h.getPreviousStatus().dbValue(),
                            h.getNewStatus().dbValue(), statusChanged, responseChanged, h.getNewResponse());
                })
                .toList();
    }
}
