package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;

@Named( "valid.txt" )
public class ValidReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            final Set<ProjectVersionRef> valid = session.getSeen();
            valid.removeAll( session.getMissing() );

            final List<ProjectVersionRef> validList = new ArrayList<ProjectVersionRef>( valid );
            Collections.sort( validList, new ToStringComparator<ProjectVersionRef>() );

            writer.printf( join( validList, "\n" ) );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
