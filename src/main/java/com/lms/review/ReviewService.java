package com.lms.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.common.domain.Book;
import com.lms.common.domain.BookRepository;
import com.lms.common.domain.BookReview;
import com.lms.common.domain.BookReviewRepository;
import com.lms.common.domain.FlagStatus;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.Member;
import com.lms.common.domain.MemberRepository;
import com.lms.common.domain.ReviewFlag;
import com.lms.common.domain.ReviewFlagRepository;
import com.lms.common.domain.ReviewModerationHistory;
import com.lms.common.domain.ReviewModerationHistoryRepository;
import com.lms.common.domain.ReviewStatus;
import com.lms.common.domain.StaffProfile;
import com.lms.common.domain.StaffProfileRepository;
import com.lms.common.security.AuditAction;
import com.lms.common.web.SelectOption;
import com.lms.review.dto.ApprovedReviewRow;
import com.lms.review.dto.BookReviewPanelView;
import com.lms.review.dto.FlagView;
import com.lms.review.dto.ModerationHistoryView;
import com.lms.review.dto.MyReviewRow;
import com.lms.review.dto.RejectForm;
import com.lms.review.dto.ReviewDetailView;
import com.lms.review.dto.ReviewFlagForm;
import com.lms.review.dto.ReviewForm;
import com.lms.review.dto.ReviewQueueRow;

@Service
@Transactional
public class ReviewService {

    static final int PAGE_SIZE = 20;
    private static final String STAFF_ROLE = "hasAuthority('Library Administrator')";

    private final BookReviewRepository reviews;
    private final ReviewFlagRepository reviewFlags;
    private final ReviewModerationHistoryRepository moderationHistory;
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final StaffProfileRepository staffProfiles;

    public ReviewService(BookReviewRepository reviews, ReviewFlagRepository reviewFlags,
            ReviewModerationHistoryRepository moderationHistory, BookRepository books, MemberRepository members,
            LoanRepository loans, StaffProfileRepository staffProfiles) {
        this.reviews = reviews;
        this.reviewFlags = reviewFlags;
        this.moderationHistory = moderationHistory;
        this.books = books;
        this.members = members;
        this.loans = loans;
        this.staffProfiles = staffProfiles;
    }

    public static List<SelectOption> statusFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All statuses"));
        for (ReviewStatus status : ReviewStatus.values()) {
            options.add(new SelectOption(status.name(), status.name()));
        }
        return options;
    }

    // ================= MEMBER SIDE =================

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<MyReviewRow> myReviews(Integer memberId) {
        return reviews.findByMemberMemberIdOrderBySubmittedAtDesc(memberId).stream()
                .map(ReviewService::toMyReviewRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookReviewPanelView panelFor(Integer bookId, Integer userId) {
        List<BookReview> approved = reviews.findByBookBookIdAndStatusOrderBySubmittedAtDesc(bookId, ReviewStatus.Approved);

        Integer viewerMemberId = userId == null ? null
                : members.findByUserUserId(userId).map(Member::getMemberId).orElse(null);

        List<ApprovedReviewRow> rows = approved.stream()
                .filter(r -> viewerMemberId == null || !r.getMember().getMemberId().equals(viewerMemberId))
                .map(r -> toApprovedRow(r, viewerMemberId))
                .toList();

        long ratingCount = approved.size();
        double sum = 0;
        for (BookReview r : approved) {
            sum += r.getRating();
        }
        Double averageRating = ratingCount == 0 ? null : Math.round((sum / ratingCount) * 10) / 10.0;

        MyReviewRow ownReview = null;
        ReviewForm ownForm = null;
        boolean canReview = false;
        if (viewerMemberId != null) {
            ownForm = new ReviewForm();
            ownForm.setBookId(bookId);
            Optional<BookReview> own = reviews.findByBookBookIdAndMemberMemberId(bookId, viewerMemberId);
            if (own.isPresent()) {
                BookReview r = own.get();
                ownReview = toMyReviewRow(r);
                ownForm.setRating(r.getRating());
                ownForm.setReviewText(r.getReviewText());
            } else {
                canReview = loans.existsByMemberMemberIdAndCopyBookBookId(viewerMemberId, bookId);
            }
        }
        return new BookReviewPanelView(rows, ownReview, ownForm, canReview, averageRating, ratingCount);
    }

    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "CREATE", entity = "BookReview")
    public BookReview submit(Integer memberId, ReviewForm form) {
        Member member = members.findById(memberId).orElseThrow(() -> new NoSuchElementException("Member not found"));
        Book book = books.findById(form.getBookId()).orElseThrow(() -> new NoSuchElementException("Book not found"));
        if (reviews.existsByBookBookIdAndMemberMemberId(form.getBookId(), memberId)) {
            throw new ReviewException("You have already reviewed this book — edit your existing review instead.");
        }
        if (!loans.existsByMemberMemberIdAndCopyBookBookId(memberId, form.getBookId())) {
            throw new ReviewException("You can only review a book you have borrowed.");
        }

        BookReview review = new BookReview();
        review.setBook(book);
        review.setMember(member);
        review.setRating(form.getRating());
        review.setReviewText(blankToNull(form.getReviewText()));
        return reviews.save(review);
    }

    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "UPDATE", entity = "BookReview")
    public BookReview edit(Integer memberId, Integer reviewId, ReviewForm form) {
        BookReview review = ownedByMember(memberId, reviewId);
        review.setRating(form.getRating());
        review.setReviewText(blankToNull(form.getReviewText()));
        review.setStatus(ReviewStatus.Pending);
        return reviews.save(review);
    }

    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "REMOVE", entity = "BookReview")
    public BookReview remove(Integer memberId, Integer reviewId) {
        BookReview review = ownedByMember(memberId, reviewId);
        if (review.getStatus() == ReviewStatus.Removed) {
            throw new ReviewException("This review has already been removed.");
        }
        review.setStatus(ReviewStatus.Removed);
        return reviews.save(review);
    }

    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "FLAG", entity = "ReviewFlag")
    public ReviewFlag flag(Integer memberId, Integer reviewId, ReviewFlagForm form) {
        BookReview review = reviews.findById(reviewId).orElseThrow(() -> new NoSuchElementException("Review not found"));
        if (review.getMember().getMemberId().equals(memberId)) {
            throw new ReviewException("You cannot flag your own review.");
        }
        if (review.getStatus() != ReviewStatus.Approved) {
            throw new ReviewException("Only a published review can be flagged.");
        }
        if (reviews.existsByReviewIdAndFlagsReportedByMemberId(reviewId, memberId)) {
            throw new ReviewException("You have already flagged this review.");
        }

        ReviewFlag flag = new ReviewFlag();
        flag.setReview(review);
        flag.setReportedBy(members.getReferenceById(memberId));
        flag.setReason(form.getReason().trim());
        reviewFlags.save(flag);
        review.getFlags().add(flag);

        recordTransition(review, ReviewStatus.Hidden, null, null);

        return flag;
    }

    private BookReview ownedByMember(Integer memberId, Integer reviewId) {
        return reviews.findById(reviewId)
                .filter(r -> r.getMember().getMemberId().equals(memberId))
                .orElseThrow(() -> new NoSuchElementException("Review not found"));
    }

    // ================= STAFF SIDE =================

    @PreAuthorize(STAFF_ROLE)
    @Transactional(readOnly = true)
    public Page<ReviewQueueRow> staffQueue(String q, String status, int page) {
        String likeQuery = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        String statusFilter = (status == null || status.isBlank()) ? null : status;
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE);
        return reviews.searchQueue(statusFilter, likeQuery, pageable).map(this::toQueueRow);
    }

    @PreAuthorize(STAFF_ROLE)
    @Transactional(readOnly = true)
    public ReviewDetailView staffDetail(Integer reviewId) {
        BookReview review = reviews.findById(reviewId).orElseThrow(() -> new NoSuchElementException("Review not found"));
        return toDetailView(review);
    }

    @PreAuthorize(STAFF_ROLE)
    @AuditAction(action = "APPROVE", entity = "BookReview")
    public BookReview approve(Integer staffUserId, Integer reviewId) {
        BookReview review = reviews.findById(reviewId).orElseThrow(() -> new NoSuchElementException("Review not found"));
        if (review.getStatus() != ReviewStatus.Pending) {
            throw new ReviewException("Only a pending review can be approved.");
        }
        recordTransition(review, ReviewStatus.Approved, null, staffOf(staffUserId));
        return review;
    }

    @PreAuthorize(STAFF_ROLE)
    @AuditAction(action = "REJECT", entity = "BookReview")
    public BookReview reject(Integer staffUserId, Integer reviewId, RejectForm form) {
        BookReview review = reviews.findById(reviewId).orElseThrow(() -> new NoSuchElementException("Review not found"));
        if (review.getStatus() != ReviewStatus.Pending) {
            throw new ReviewException("Only a pending review can be rejected.");
        }
        recordTransition(review, ReviewStatus.Rejected, form.getReason().trim(), staffOf(staffUserId));
        return review;
    }

    @PreAuthorize(STAFF_ROLE)
    @AuditAction(action = "UPHOLD", entity = "BookReview")
    public BookReview uphold(Integer staffUserId, Integer reviewId) {
        BookReview review = reviews.findById(reviewId).orElseThrow(() -> new NoSuchElementException("Review not found"));
        if (review.getStatus() != ReviewStatus.Hidden) {
            throw new ReviewException("Only a flagged review awaiting a decision can be upheld.");
        }
        resolveOpenFlags(review, FlagStatus.Upheld);
        recordTransition(review, ReviewStatus.Removed, null, staffOf(staffUserId));
        return review;
    }

    @PreAuthorize(STAFF_ROLE)
    @AuditAction(action = "DISMISS", entity = "BookReview")
    public BookReview dismiss(Integer staffUserId, Integer reviewId) {
        BookReview review = reviews.findById(reviewId).orElseThrow(() -> new NoSuchElementException("Review not found"));
        if (review.getStatus() != ReviewStatus.Hidden) {
            throw new ReviewException("Only a flagged review awaiting a decision can be dismissed.");
        }
        resolveOpenFlags(review, FlagStatus.Dismissed);
        recordTransition(review, ReviewStatus.Approved, null, staffOf(staffUserId));
        return review;
    }

    private StaffProfile staffOf(Integer staffUserId) {
        return staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));
    }

    private static void resolveOpenFlags(BookReview review, FlagStatus resolution) {
        review.getFlags().stream()
                .filter(f -> f.getStatus() == FlagStatus.Open)
                .forEach(f -> f.setStatus(resolution));
    }

    private void recordTransition(BookReview review, ReviewStatus newStatus, String reason, StaffProfile moderator) {
        ReviewStatus previousStatus = review.getStatus();
        review.setStatus(newStatus);
        reviews.save(review);

        ReviewModerationHistory history = new ReviewModerationHistory();
        history.setReview(review);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setReason(reason);
        history.setModerator(moderator);
        moderationHistory.save(history);
        review.getModerationHistory().add(history);
    }

    // ---- Shared view-building ----

    private static MyReviewRow toMyReviewRow(BookReview r) {
        return new MyReviewRow(r.getReviewId(), r.getBook().getBookId(), r.getBook().getTitle(), r.getRating(),
                r.getStatus().name(), r.getSubmittedAt());
    }

    private ApprovedReviewRow toApprovedRow(BookReview r, Integer viewerMemberId) {
        String memberName = r.getMember().getUser().getFirstName() + " " + r.getMember().getUser().getLastName();
        boolean canFlag = viewerMemberId != null
                && !reviews.existsByReviewIdAndFlagsReportedByMemberId(r.getReviewId(), viewerMemberId);
        return new ApprovedReviewRow(r.getReviewId(), memberName, r.getRating(), r.getReviewText(), r.getSubmittedAt(), canFlag);
    }

    private ReviewQueueRow toQueueRow(BookReview r) {
        String memberName = r.getMember().getUser().getFirstName() + " " + r.getMember().getUser().getLastName();
        return new ReviewQueueRow(r.getReviewId(), r.getBook().getTitle(), memberName, r.getRating(),
                excerpt(r.getReviewText()), r.getStatus().name(), r.getFlags().size(), r.getSubmittedAt());
    }

    private ReviewDetailView toDetailView(BookReview r) {
        String memberName = r.getMember().getUser().getFirstName() + " " + r.getMember().getUser().getLastName();
        List<FlagView> flags = r.getFlags().stream()
                .sorted(Comparator.comparing(ReviewFlag::getReportedAt))
                .map(f -> new FlagView(f.getReviewFlagId(),
                        f.getReportedBy().getUser().getFirstName() + " " + f.getReportedBy().getUser().getLastName(),
                        f.getReason(), f.getStatus().name(), f.getReportedAt()))
                .toList();
        List<ModerationHistoryView> history = r.getModerationHistory().stream()
                .sorted(Comparator.comparing(ReviewModerationHistory::getModeratedAt))
                .map(h -> new ModerationHistoryView(h.getModeratedAt(),
                        h.getModerator() == null ? "System" : h.getModerator().getUser().getFirstName() + " " + h.getModerator().getUser().getLastName(),
                        h.getPreviousStatus().name(), h.getNewStatus().name(), h.getReason()))
                .toList();
        boolean hasOpenFlags = r.getFlags().stream().anyMatch(f -> f.getStatus() == FlagStatus.Open);
        return new ReviewDetailView(r.getReviewId(), r.getBook().getTitle(), r.getBook().getBookId(), memberName,
                r.getRating(), r.getReviewText(), r.getStatus().name(), r.getSubmittedAt(), r.getUpdatedAt(),
                hasOpenFlags, flags, history);
    }

    private static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 140 ? text.substring(0, 140) + "…" : text;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
