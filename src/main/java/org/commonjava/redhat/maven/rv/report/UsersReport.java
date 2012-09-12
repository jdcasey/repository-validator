package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "users.txt" )
public class UsersReport
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
            for ( final ProjectVersionRef seen : session.getSeen() )
            {
                final Set<ProjectRelationship<?>> userRelationships = web.getUserRelationships( seen );
                writer.printf( "\n\n%s\n-------------------------------------\n\n  %s", seen,
                               join( userRelationships, "\n  " ) );
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
