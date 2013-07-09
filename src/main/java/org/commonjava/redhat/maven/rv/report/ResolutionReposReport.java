package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "resolution-repos.log" )
public class ResolutionReposReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );
            final Map<ArtifactRef, List<String>> allByArtifact = session.getAllArtifactResolutionRepositories();

            for ( final Map.Entry<ArtifactRef, List<String>> entry : allByArtifact.entrySet() )
            {
                writer.printf( "%s:\n----------------------------------------\n", entry.getKey() );
                final List<String> repos = entry.getValue();
                if ( repos == null || repos.isEmpty() )
                {
                    writer.printf( "\n  -None-" );
                }
                else
                {
                    final List<String> r = new ArrayList<String>( repos );
                    Collections.sort( r );

                    for ( final String repo : r )
                    {
                        writer.printf( "\n  %s", repo );
                    }
                }

                writer.println();
                writer.println();
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    public boolean canRun( final ValidatorSession session )
    {
        return session.getAllArtifactResolutionRepositories() != null;
    }

}
