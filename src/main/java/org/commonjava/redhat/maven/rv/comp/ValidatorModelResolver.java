package org.commonjava.redhat.maven.rv.comp;

import java.io.File;
import java.util.List;

import org.apache.maven.mae.project.internal.SimpleModelResolver;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.DefaultRequestTrace;
import org.sonatype.aether.util.artifact.DefaultArtifact;

public class ValidatorModelResolver
    implements ModelResolver
{

    private final SimpleModelResolver delegate;

    private DirWorkspaceReader workspaceReader;

    public ValidatorModelResolver( final ValidatorSession session, final String pomPath,
                                   final ArtifactResolver artifactResolver,
                                   final RemoteRepositoryManager remoteRepositoryManager )
    {
        final RepositorySystemSession rss = session.getRepositorySystemSession();
        final List<RemoteRepository> repos = session.getRemoteRepositoriesForResolution();

        delegate =
            new SimpleModelResolver( rss, repos, new DefaultRequestTrace( pomPath ), artifactResolver,
                                     remoteRepositoryManager );

        this.workspaceReader = new DirWorkspaceReader( session.getRepositoryDirectory() );
    }

    public ModelSource resolveModel( final String groupId, final String artifactId, final String version )
        throws UnresolvableModelException
    {

        final File pom = workspaceReader.findArtifact( new DefaultArtifact( groupId, artifactId, "pom", version ) );
        if ( pom != null )
        {
            return new FileModelSource( pom );
        }

        return delegate.resolveModel( groupId, artifactId, version );
    }

    public void addRepository( final Repository repository )
        throws InvalidRepositoryException
    {
        delegate.addRepository( repository );
    }

    public ModelResolver newCopy()
    {
        return this;
    }

}
