package com.lms.feedback;

/**
 * Thrown by {@link FeedbackService} for every UC-10 refusal case that is
 * not a stale id: editing or withdrawing feedback staff have already
 * responded to, reviewing feedback that is already Closed, or a staff
 * review that changes nothing. Shown as a flash-attribute error toast,
 * never a stack trace.
 */
public class FeedbackException extends RuntimeException {

    public FeedbackException(String message) {
        super(message);
    }
}
