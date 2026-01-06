package org.letsemploy.ojobpub_publisher.tag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepo tagRepo;

    public List<Tag> findAll() {
        return tagRepo.findAllByOrderByNameAsc();
    }

    public Tag findById(Long id) {
        return tagRepo.findById(id).orElseThrow(() -> new NotFoundException("Tag not found: " + id));
    }

    public List<Tag> search(String query) {
        if (query == null || query.isBlank()) {
            return tagRepo.findAllByOrderByNameAsc().stream().limit(20).toList();
        }
        return tagRepo.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(Tag.normalize(query));
    }

    public long jobCount(Long id) {
        return tagRepo.countJobs(id);
    }

    @Transactional
    public Tag save(Long id, String rawName) {
        String name = Tag.normalize(rawName);
        if (name == null || name.isBlank()) {
            throw new ValidationFailure("name", "A name is required.");
        }
        if (name.length() > Tag.MAX_LENGTH) {
            throw new ValidationFailure("name",
                    "At most " + Tag.MAX_LENGTH + " characters; the published schema caps tags there.");
        }
        Tag existing = tagRepo.findFirstByNameIgnoreCase(name).orElse(null);
        if (existing != null && (id == null || !existing.getId().equals(id))) {
            throw new ValidationFailure("name", "A tag with this name already exists.");
        }
        Tag tag = id == null ? new Tag() : findById(id);
        tag.setName(name);
        return tagRepo.save(tag);
    }

    /** Creating a tag that already exists returns the existing one (spec 3.4). */
    @Transactional
    public Tag findOrCreate(String rawName) {
        String name = Tag.normalize(rawName);
        return tagRepo.findFirstByNameIgnoreCase(name).orElseGet(() -> save(null, name));
    }

    @Transactional
    public void delete(Long id) {
        tagRepo.delete(findById(id));
    }
}
