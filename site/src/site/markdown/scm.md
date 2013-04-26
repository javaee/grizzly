## Checking Out the Sources

It's recommended that developers use SSH for interacting with java.net Git repositories.
This allows you to not have to deal with proxy configuration, which usually doesn't work anyway.

Once your java.net account is available, follow these [instructions][sshkeygen] for
generating your SSH key and associating it with your account.

Give the association about five minutes to take effect.  If everything is configured
properly, the repository can be cloned by invoking:  `git clone ssh://<java.net ID>@git.java.net/grizzly~git`

If you're only interested in reading the latest version of the sources and do not wish
to a) contribute code back to the repository or b) do not care about the history,
you can speed up the clone process by invoking `git clone --depth 1 ssh://<java.net ID>@git.java.net/grizzly~git` instead.
This should speed up the clone process considerably.

[sshkeygen]: http://java.net/projects/help/pages/GeneratingAnSSHKey

## Working with Branches and Tags

Branch information:

<table>
    <tr>
        <th>Branch Name</th>
        <th>Details</th>
    </tr>
    <tr>
        <td>master</td>
        <td>This is effectively Grizzly 3.0.</td>
    </tr>
    <tr>
        <td>2.3.x</td>
        <td>This is the latest stable release branch.  New features and sustaining work happen here.</td>
    </tr>
    <tr>
        <td>2.2.x</td>
        <td>Grizzly 2.2 sustaining.</td>
    </tr>
    <tr>
        <td>2.1.x</td>
        <td>Grizzly 2.1 sustaining.</td>
    </tr>
    <tr>
        <td>1.9.x</td>
        <td>Grizzly 1.9 sustaining.</td>
    </tr>
    <tr>
        <td>1.0.x</td>
        <td>Grizzly 1.0 sustaining.</td>
    </tr>
</table>

In order to check out a branch in git, you simply need to issue
`git checkout <branch name>` (e.g., `git checkout 2.2.x` or `git checkout master`).

If you're interested in working with an existing tag, you'll first need to issue
`git fetch --tags` in order to obtain the tag references.  After successful completion
of this command, you can issue `git checkout <tag name>` Note that when doing so, you'll
get a message about being in a detached state - this is normal and nothing to worry about.
All fetched tags can be listed using `git tag -l` In general, we keep our tag names
inline with the released version.  For example, if you wanted to checkout the tag
for Grizzly 2.2.3, the tag name would be *2_2_3* This convention is consistent for
all branches/versions/releases.

## GIT Tips and Tricks for Developers

First, for anyone not familiar with Git, before attempting to work with the repository,
we highly recommend reading the [Git tutorial][gitorial].

When collaborating, before you push your changes to the remote repository, it's best
to issue `git pull --rebase` This will 'replay' any changes that have occurred in the
remote repository since your last pull on top of your current work.  If you don't do this,
Git will perform a merge for you, however, the result of the commit will look like
you've touched files that you haven't.  This is fine, but it generally raises a few eyebrows.

There are times when we need to move changes back and forth between branches.
In cases where the code bases are very similar (master and 2.2.x as an example) you can
use `git cherry-pick <SHA1 of the commit to pick and apply>` to do this quickly.

[gitorial]: http://schacon.github.com/git/gittutorial.html