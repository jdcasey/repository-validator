package org.commonjava.redhat.maven.rv.comp;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.util.logging.Logger;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

public class DirWorkspaceReader
    implements WorkspaceReader
{

    private final Logger logger = new Logger( getClass() );

    private char FS = File.separatorChar;

    private File dir;

    private final WorkspaceRepository repo = new WorkspaceRepository( "validator", "validator" );

    public DirWorkspaceReader( final File dir )
    {
        this.dir = dir;
    }

    public File findArtifact( final Artifact a )
    {
        final StringBuilder path = new StringBuilder();
        path.append( a.getGroupId()
                      .replace( '.', FS ) )
            .append( FS )
            .append( a.getArtifactId() )
            .append( FS )
            .append( a.getVersion() )
            .append( FS )
            .append( a.getArtifactId() )
            .append( '-' )
            .append( a.getVersion() );

        if ( a.getClassifier() != null )
        {
            path.append( '-' )
                .append( a.getClassifier() );
        }

        path.append( '.' )
            .append( a.getExtension() );

        final File f = new File( dir, path.toString() );
        if ( f.exists() )
        {
            return f;
        }

        return null;
    }

    public List<String> findVersions( final Artifact a )
    {
        final StringBuilder path = new StringBuilder();
        path.append( a.getGroupId()
                      .replace( '.', FS ) )
            .append( FS )
            .append( a.getArtifactId() )
            .append( FS )
            .append( "maven-metadata.xml" );

        File f = new File( dir, path.toString() );
        if ( f.exists() )
        {
            FileInputStream in = null;
            try
            {
                in = new FileInputStream( f );
                final Metadata md = new MetadataXpp3Reader().read( in );
                final Versioning versioning = md.getVersioning();
                if ( versioning != null )
                {
                    return versioning.getVersions();
                }
            }
            catch ( final IOException e )
            {
                logger.error( "Failed to read metadata: %s. Reason: %s", e, f, e.getMessage() );
            }
            catch ( final XmlPullParserException e )
            {
                logger.error( "Failed to parse metadata: %s. Reason: %s", e, f, e.getMessage() );
            }
            finally
            {
                closeQuietly( in );
            }
        }
        else
        {
            f = findArtifact( a );
            if ( f != null )
            {
                return Collections.singletonList( a.getVersion() );
            }
        }

        return null;
    }

    public WorkspaceRepository getRepository()
    {
        return repo;
    }

}
