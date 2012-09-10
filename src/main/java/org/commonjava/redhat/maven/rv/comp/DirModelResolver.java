package org.commonjava.redhat.maven.rv.comp;

import java.io.File;

import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;

public class DirModelResolver
    implements ModelResolver
{

    private char FS = File.separatorChar;

    private File dir;

    public DirModelResolver( final File dir )
    {
        this.dir = dir;
    }

    public ModelSource resolveModel( final String groupId, final String artifactId, final String version )
        throws UnresolvableModelException
    {
        final StringBuilder path = new StringBuilder();
        path.append( groupId.replace( '.', FS ) )
            .append( FS )
            .append( artifactId )
            .append( FS )
            .append( version )
            .append( FS )
            .append( artifactId )
            .append( '-' )
            .append( version )
            .append( ".pom" );

        return new FileModelSource( new File( dir, path.toString() ) );
    }

    public void addRepository( final Repository repository )
        throws InvalidRepositoryException
    {
    }

    public ModelResolver newCopy()
    {
        return this;
    }

}
