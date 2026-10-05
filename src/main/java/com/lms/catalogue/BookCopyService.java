package com.lms.catalogue;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.catalogue.dto.BookCopyForm;
import com.lms.common.domain.Book;
import com.lms.common.domain.BookCopy;
import com.lms.common.domain.BookCopyRepository;
import com.lms.common.domain.BookCopyStatus;
import com.lms.common.domain.CopyCondition;
import com.lms.common.domain.BookRepository;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.LoanStatus;

/**
 * UC-02 — the {@link BookCopy} half of "Book Catalogue and Inventory": one
 * physical, shelvable copy of a {@link Book}. Read access is public (a
 * guest's book-detail page shows every copy's status pill, availability
 * and shelf location — PB-22/PB-23); every write is staff-only.
 *
 * <p><b>Why this service never sets {@code Status} to anything but {@code
 * Available} (on create) or {@code Withdrawn} (on {@link #withdraw}).</b>
 * {@code database/01_schema.sql}'s own comment on {@code CREATE TABLE
 * BookCopy} says the column mixes loan-driven states with decision states,
 * and that "[D5] triggers on Loan, Reservation and BookIncident are its
 * only writers." Those triggers are a later project phase and do not exist
 * yet. Until they do, this is the one place in the codebase that can move
 * a copy at all — so it deliberately only ever performs the one inventory
 * transition that is genuinely Catalogue's job and no one else's: retiring
 * a copy from circulation. It never simulates a loan, a hold, a repair, a
 * loss or a damage report — those states stay reserved for Borrowing,
 * Reservation and the incident/fine workflow once they exist, exactly as
 * R24 intends.
 */
@Service
@Transactional
public class BookCopyService {

    private final BookCopyRepository copies;
    private final BookRepository books;
    private final LoanRepository loans;

    public BookCopyService(BookCopyRepository copies, BookRepository books, LoanRepository loans) {
        this.copies = copies;
        this.books = books;
        this.loans = loans;
    }

    /** The book detail page's copy table — public read. */
    @Transactional(readOnly = true)
    public List<BookCopy> forBook(Integer bookId) {
        return copies.findByBookBookIdOrderByAccessionNumber(bookId);
    }

    @Transactional(readOnly = true)
    public BookCopy findById(Integer copyId) {
        BookCopy copy = copies.findById(copyId).orElseThrow(() -> new NoSuchElementException("Copy not found"));
        // BookCopy.book is @ManyToOne(LAZY) — BookCopyController reads
        // copy.getBook().getTitle()/getBookId() after this transaction (and
        // its Hibernate session) has already closed. Same fix as
        // BookService#findById: touch it once here, while the session is
        // still open, so it is safely readable afterwards.
        copy.getBook().getTitle();
        return copy;
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public BookCopy create(Integer bookId, BookCopyForm form) {
        Book book = books.findById(bookId).orElseThrow(() -> new NoSuchElementException("Book not found"));
        rejectDuplicateIdentifiers(form, null);
        BookCopy copy = new BookCopy();
        copy.setBook(book);
        applyForm(copy, form);
        // Status is left at the entity's own default (Available) — see class javadoc.
        return copies.save(copy);
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public BookCopy update(Integer copyId, BookCopyForm form) {
        BookCopy copy = findById(copyId);
        rejectDuplicateIdentifiers(form, copyId);
        // CK_BookCopy_ReferenceNotCirculating: a copy currently out or held cannot become reference-only.
        if (form.isReferenceOnly() && (copy.getStatus() == BookCopyStatus.OnLoan || copy.getStatus() == BookCopyStatus.OnHold)) {
            throw new ActiveLoanException(
                    "This copy is currently " + copy.getStatus().dbValue().toLowerCase()
                            + " and cannot be marked reference-only until it is back.");
        }
        applyForm(copy, form);
        return copies.save(copy);
    }

    private void rejectDuplicateIdentifiers(BookCopyForm form, Integer excludingCopyId) {
        copies.findByAccessionNumber(form.getAccessionNumber())
                .filter(existing -> excludingCopyId == null || !existing.getCopyId().equals(excludingCopyId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("accessionNumber",
                            "Accession number " + form.getAccessionNumber() + " is already in use.");
                });
        copies.findByBarcode(form.getBarcode())
                .filter(existing -> excludingCopyId == null || !existing.getCopyId().equals(excludingCopyId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("barcode",
                            "Barcode " + form.getBarcode() + " is already in use.");
                });
    }

    private void applyForm(BookCopy copy, BookCopyForm form) {
        copy.setAccessionNumber(form.getAccessionNumber().trim());
        copy.setBarcode(form.getBarcode().trim());
        copy.setShelfLocation(blankToNull(form.getShelfLocation()));
        copy.setAcquisitionDate(form.getAcquisitionDate());
        copy.setPurchasePrice(form.getPurchasePrice());
        copy.setCopyCondition(CopyCondition.valueOf(form.getCopyCondition()));
        copy.setReferenceOnly(form.isReferenceOnly());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public BookCopyForm forEdit(Integer copyId) {
        BookCopy copy = findById(copyId);
        BookCopyForm form = new BookCopyForm();
        form.setAccessionNumber(copy.getAccessionNumber());
        form.setBarcode(copy.getBarcode());
        form.setShelfLocation(copy.getShelfLocation());
        form.setAcquisitionDate(copy.getAcquisitionDate());
        form.setPurchasePrice(copy.getPurchasePrice());
        form.setCopyCondition(copy.getCopyCondition().name());
        form.setReferenceOnly(copy.isReferenceOnly());
        return form;
    }

    /**
     * The copy-level "deactivate" (UC-02's own deactivate flow is stated at
     * the book level — "delete a record that has no active borrowing
     * transactions" — this is the same idea applied to one copy rather
     * than a whole title). Blocked while the copy is out or held, the same
     * guard {@link BookService#deactivate} applies to a whole book.
     */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public void withdraw(Integer copyId) {
        BookCopy copy = findById(copyId);
        if (loans.findByCopyCopyIdAndStatus(copyId, LoanStatus.Active).isPresent()) {
            throw new ActiveLoanException(
                    "Copy " + copy.getAccessionNumber() + " is currently on loan and cannot be withdrawn "
                            + "until it is returned.");
        }
        if (copy.getStatus() == BookCopyStatus.OnHold) {
            throw new ActiveLoanException(
                    "Copy " + copy.getAccessionNumber() + " is currently held for a reservation and cannot "
                            + "be withdrawn.");
        }
        copy.setStatus(BookCopyStatus.Withdrawn);
        copies.save(copy);
    }
}
