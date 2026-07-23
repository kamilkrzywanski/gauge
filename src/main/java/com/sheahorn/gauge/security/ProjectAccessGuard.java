package com.sheahorn.gauge.security;

import com.sheahorn.gauge.domain.Issue;
import com.sheahorn.gauge.domain.Project;
import com.sheahorn.gauge.domain.Task;
import com.sheahorn.gauge.domain.Tasklist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Checks whether the current API key is allowed to access a given project
 * (or entity that belongs to a project tree).
 */
@ApplicationScoped
public class ProjectAccessGuard {

    @Inject
    CurrentApiKey currentApiKey;

    /**
     * Returns true if the current API key is allowed to access the given project.
     * Unrestricted keys (including master) can access everything.
     * Restricted keys can only access projects whose root ancestor is in their
     * restricted set.
     */
    public boolean canAccessProject(String projectId) {
        ApiKey key = currentApiKey.get();
        if (key == null) {
            // No API key set (session auth) — allow
            return true;
        }
        if (!key.isRestricted()) {
            return true;
        }
        // Find the root ancestor of this project
        String rootId = findRootProjectId(projectId);
        return key.restrictedProjectIds.contains(rootId);
    }

    /**
     * Returns true if the current API key is allowed to access the given issue.
     */
    public boolean canAccessIssue(String issueId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        Issue issue = Issue.findById(issueId);
        if (issue == null) {
            return false;
        }
        return canAccessProject(issue.projectId);
    }

    /**
     * Returns true if the current API key is allowed to access the given tasklist.
     */
    public boolean canAccessTasklist(String tasklistId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        Tasklist tasklist = Tasklist.findById(tasklistId);
        if (tasklist == null) {
            return false;
        }
        return canAccessIssue(tasklist.issueId);
    }

    /**
     * Returns true if the current API key is allowed to access the given task.
     */
    public boolean canAccessTask(String taskId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        Task task = Task.findById(taskId);
        if (task == null) {
            return false;
        }
        return canAccessTasklist(task.tasklistId);
    }

    /**
     * Walks up the parent chain to find the root project ID.
     */
    private String findRootProjectId(String projectId) {
        String current = projectId;
        while (current != null) {
            Project p = Project.findById(current);
            if (p == null) {
                return current; // shouldn't happen, but be safe
            }
            if (p.parentId == null) {
                return p.id;
            }
            current = p.parentId;
        }
        return projectId;
    }
}
