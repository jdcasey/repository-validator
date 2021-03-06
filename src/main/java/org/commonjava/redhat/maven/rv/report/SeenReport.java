package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;

@Named( "processed.txt" )
public class SeenReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            final List<ProjectVersionRef> list = new ArrayList<ProjectVersionRef>( session.getSeen() );
            Collections.sort( list, new ToStringComparator<ProjectVersionRef>() );

            writer.printf( join( list, "\n" ) );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    public boolean canRun( final ValidatorSession session )
    {
        return session.getSeen() != null;
    }

}
