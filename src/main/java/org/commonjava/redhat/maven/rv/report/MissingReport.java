package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;

@Named( "missing.txt" )
public class MissingReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            final List<ProjectVersionRef> list = filterAndSort( session.getMissing() );

            writer.printf( join( list, "\n" ) );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    private List<ProjectVersionRef> filterAndSort( final Set<ProjectVersionRef> missing )
    {
        final Set<ProjectVersionRef> filtered = new HashSet<ProjectVersionRef>();
        for ( final ProjectVersionRef ref : missing )
        {
            if ( ref instanceof ArtifactRef )
            {
                continue;
            }

            filtered.add( ref );
        }

        final List<ProjectVersionRef> list = new ArrayList<ProjectVersionRef>( filtered );
        Collections.sort( list, new ToStringComparator<ProjectVersionRef>() );

        return list;
    }

    public boolean canRun( final ValidatorSession session )
    {
        return session.getMissing() != null;
    }

}
