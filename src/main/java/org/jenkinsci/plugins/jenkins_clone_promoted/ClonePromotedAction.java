package org.jenkinsci.plugins.jenkins_clone_promoted;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.promoted_builds.Promotion;
import hudson.plugins.promoted_builds.PromotionProcess;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Clones the branch that was promoted after clearing the workspace.
 *
 * @author jason@stiefel.io
 */
public class ClonePromotedAction extends Builder implements Serializable {

    private static final String PROMOTED_GIT_URL = "PROMOTED_GIT_URL";
    private static final String PROMOTED_GIT_BRANCH = "PROMOTED_GIT_BRANCH";

    @DataBoundConstructor
    public ClonePromotedAction() {}

    @Override
    public boolean perform(AbstractBuild<?, ?> _build, Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {

        final Promotion build = (Promotion)_build;
        if (!(build.getTarget().getProject().getScm() instanceof GitSCM)) {
            listener.error("[clone-promoted] Promoted build does not use the GitSCM!");
            return false;
        }

        final EnvVars environment = build.getEnvironment(listener);
        final String gitBranch = environment.get(PROMOTED_GIT_BRANCH);
        if (gitBranch == null)
            throw new IllegalStateException(PROMOTED_GIT_BRANCH + " environment variable is not present");
        final String gitUrl = environment.get(PROMOTED_GIT_URL);
        if (gitUrl == null)
            throw new IllegalStateException(PROMOTED_GIT_URL + " environment variable is not present");
        final String localBranch = gitBranch.substring(gitBranch.indexOf('/') + 1);

        FilePath workspace = build.getWorkspace();

        listener.getLogger().println("[clone-promoted] Clearing out " + workspace.getRemote());
        workspace.deleteContents();

        GitSCM scm = (GitSCM)build.getTarget().getProject().getScm();
        final String gitExe = scm.getGitExe(build.getBuiltOn(), listener);

        workspace.act(new FilePath.FileCallable<Void>() {

            private static final long serialVersionUID = 1L;

            public Void invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {

                listener.getLogger().println("[clone-promoted] Cloning " +
                        gitUrl + " " + gitBranch + " into " + workspace + " " + localBranch);

                GitClient git = Git.with(listener, environment).in(workspace).using(gitExe).getClient();

                git.clone(gitUrl, "origin", false, null);
                git.fetch("origin", null);
                git.checkoutBranch(localBranch, gitBranch);

                return null;
            }
        });

        listener.getLogger().println("[clone-promoted] Complete");

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return jobType == PromotionProcess.class;
        }
        @Override
        public String getDisplayName() {
            return "Clone Promoted Git Branch";
        }
    }

}
