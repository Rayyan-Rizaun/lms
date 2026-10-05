package com.lms.catalogue;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.catalogue.dto.PublisherForm;
import com.lms.common.domain.Publisher;
import com.lms.common.domain.PublisherRepository;

/**
 * UC-02 reference data: {@link Publisher}. Same shape as {@link
 * AuthorService} — staff-only maintenance of the list {@link
 * BookService#publisherOptions()} draws the Book form's publisher
 * dropdown from.
 */
@Service
@Transactional
public class PublisherService {

    private final PublisherRepository publishers;

    public PublisherService(PublisherRepository publishers) {
        this.publishers = publishers;
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public List<Publisher> findAll() {
        return publishers.findAll(Sort.by("publisherName"));
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public Publisher findById(Integer id) {
        return publishers.findById(id).orElseThrow(() -> new NoSuchElementException("Publisher not found"));
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Publisher create(PublisherForm form) {
        rejectDuplicateName(form, null);
        Publisher publisher = new Publisher();
        applyForm(publisher, form);
        return publishers.save(publisher);
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Publisher update(Integer id, PublisherForm form) {
        Publisher publisher = findById(id);
        rejectDuplicateName(form, id);
        applyForm(publisher, form);
        return publishers.save(publisher);
    }

    private void rejectDuplicateName(PublisherForm form, Integer excludingId) {
        publishers.findByPublisherNameIgnoreCase(form.getPublisherName().trim())
                .filter(existing -> excludingId == null || !existing.getPublisherId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("publisherName",
                            "\"" + form.getPublisherName() + "\" already exists.");
                });
    }

    private void applyForm(Publisher publisher, PublisherForm form) {
        publisher.setPublisherName(form.getPublisherName().trim());
        publisher.setWebsite(form.getWebsite() == null || form.getWebsite().isBlank()
                ? null : form.getWebsite().trim());
    }

    /** Same reasoning as {@link AuthorService#deactivate} — no "in use" guard needed. */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public void deactivate(Integer id) {
        Publisher publisher = findById(id);
        publisher.setActive(false);
        publishers.save(publisher);
    }
}
