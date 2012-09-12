package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "missing-users.txt" )
public class MissingUsersReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            final EProjectWeb web = session.getProjectWeb();
            final Set<ProjectVersionRef> processed = new HashSet<ProjectVersionRef>();

            for ( ProjectVersionRef ref : session.getMissing() )
            {
                if ( ref instanceof ArtifactRef )
                {
                    ref = ( (ArtifactRef) ref ).asProjectVersionRef();
                }

                if ( processed.contains( ref ) )
                {
                    continue;
                }

                final Set<ProjectRelationship<?>> userRelationships = web.getUserRelationships( ref );
                writer.printf( "\n\n%s\n-------------------------------------\n\n  %s", ref,
                               join( userRelationships, "\n  " ) );

                processed.add( ref );
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
