package com.sheahorn.gauge.security;

import com.sheahorn.gauge.domain.*;
import com.sheahorn.gauge.repository.IssueRepository;
import com.sheahorn.gauge.repository.ProjectRepository;
import com.sheahorn.gauge.repository.TasklistRepository;
import com.sheahorn.gauge.repository.TaskRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectAccessGuardTest {

    @Inject
    ProjectAccessGuard accessGuard;

    @Inject
    CurrentApiKey currentApiKey;

    @Inject
    ProjectRepository projectRepository;

    @Inject
    IssueRepository issueRepository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM tasks").executeUpdate();
        em.createNativeQuery("DELETE FROM tasklists").executeUpdate();
        em.createNativeQuery("DELETE FROM issues").executeUpdate();
        em.createNativeQuery("DELETE FROM projects").executeUpdate();
        currentApiKey.set(null);
    }

    // ---- helpers ----

    private Project createProject(String id, String name, String parentId) {
        Project p = new Project(id, name, "desc", parentId, "");
        projectRepository.save(p);
        return p;
    }

    private Issue createIssue(String id, String projectId) {
        Issue i = new Issue(id, projectId, "Issue " + id, "desc", IssueStatus.TODO, Priority.NORMAL);
        issueRepository.save(i);
        return i;
    }

    private Tasklist createTasklist(String id, String issueId) {
        Tasklist tl = new Tasklist(id, issueId, "TL " + id, TasklistStatus.TODO, null);
        tasklistRepository.save(tl);
        return tl;
    }

    private Task createTask(String id, String tasklistId) {
        Task t = new Task(id, tasklistId, 0, "Task " + id, "desc", TaskStatus.TODO);
        taskRepository.save(t);
        return t;
    }

    private void setKey(Set<String> restrictedProjectIds) {
        currentApiKey.set(new ApiKey("user1", "testuser", "user", restrictedProjectIds));
    }

    // ---- canAccessProject ----

    @Test
    @Transactional
    void testCanAccessProjectNullKey() {
        // No key set (session auth) — always allowed
        currentApiKey.set(null);
        Project root = createProject("ROOT0001", "Root", null);
        assertTrue(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectUnrestrictedKey() {
        // Unrestricted key (null restrictedProjectIds) — always allowed
        setKey(null);
        Project root = createProject("ROOT0001", "Root", null);
        assertTrue(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectUnrestrictedKeyEmptySet() {
        // Unrestricted key (empty set) — always allowed
        setKey(Set.of());
        Project root = createProject("ROOT0001", "Root", null);
        assertTrue(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedAllowedRoot() {
        // Restricted key with root in set — allowed
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0001", "Root", null);
        assertTrue(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedDisallowedRoot() {
        // Restricted key with different root — denied
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0002", "Other Root", null);
        assertFalse(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedNestedSubproject() {
        // Restricted key allows ROOT0001 — subproject under it should be allowed
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0001", "Root", null);
        Project child = createProject("CHILD001", "Child", root.id());
        Project grandchild = createProject("GRAND001", "Grandchild", child.id());

        assertTrue(accessGuard.canAccessProject(child.id()));
        assertTrue(accessGuard.canAccessProject(grandchild.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedNestedSubprojectDisallowed() {
        // Restricted key allows ROOT0001 — subproject under ROOT0002 should be denied
        setKey(Set.of("ROOT0001"));
        Project otherRoot = createProject("ROOT0002", "Other Root", null);
        Project child = createProject("CHILD001", "Child", otherRoot.id());

        assertFalse(accessGuard.canAccessProject(child.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedMultipleRoots() {
        // Restricted key allows ROOT0001 and ROOT0003
        setKey(Set.of("ROOT0001", "ROOT0003"));
        Project root1 = createProject("ROOT0001", "Root1", null);
        Project root2 = createProject("ROOT0002", "Root2", null);
        Project root3 = createProject("ROOT0003", "Root3", null);

        assertTrue(accessGuard.canAccessProject(root1.id()));
        assertFalse(accessGuard.canAccessProject(root2.id()));
        assertTrue(accessGuard.canAccessProject(root3.id()));
    }

    // ---- canAccessIssue ----

    @Test
    @Transactional
    void testCanAccessIssueNullKey() {
        currentApiKey.set(null);
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertTrue(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueUnrestrictedKey() {
        setKey(null);
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertTrue(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueRestrictedAllowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertTrue(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueRestrictedDisallowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0002", "Other Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertFalse(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueNonexistent() {
        setKey(Set.of("ROOT0001"));
        assertFalse(accessGuard.canAccessIssue("NONEXISTENT"));
    }

    // ---- canAccessTasklist ----

    @Test
    @Transactional
    void testCanAccessTasklistNullKey() {
        currentApiKey.set(null);
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertTrue(accessGuard.canAccessTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanAccessTasklistUnrestrictedKey() {
        setKey(null);
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertTrue(accessGuard.canAccessTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanAccessTasklistRestrictedAllowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertTrue(accessGuard.canAccessTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanAccessTasklistRestrictedDisallowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0002", "Other Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertFalse(accessGuard.canAccessTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanAccessTasklistNonexistent() {
        setKey(Set.of("ROOT0001"));
        assertFalse(accessGuard.canAccessTasklist("NONEXISTENT"));
    }

    // ---- canAccessTask ----

    @Test
    @Transactional
    void testCanAccessTaskNullKey() {
        currentApiKey.set(null);
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskUnrestrictedKey() {
        setKey(null);
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskRestrictedAllowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskRestrictedDisallowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0002", "Other Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertFalse(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskNonexistent() {
        setKey(Set.of("ROOT0001"));
        assertFalse(accessGuard.canAccessTask("NONEXISTENT"));
    }

    // ---- deep chain: task → tasklist → issue → subproject → root ----

    @Test
    @Transactional
    void testCanAccessTaskDeepChainAllowed() {
        setKey(Set.of("ROOT0001"));
        Project root = createProject("ROOT0001", "Root", null);
        Project child = createProject("CHILD001", "Child", root.id());
        Issue issue = createIssue("ISS00001", child.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());

        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskDeepChainDisallowed() {
        setKey(Set.of("ROOT0001"));
        Project otherRoot = createProject("ROOT0002", "Other Root", null);
        Project child = createProject("CHILD001", "Child", otherRoot.id());
        Issue issue = createIssue("ISS00001", child.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());

        assertFalse(accessGuard.canAccessTask(t.id()));
    }
}
