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
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;
import org.commonjava.util.logging.Logger;

@Named( "missing-impacts-2.txt" )
public class MissingImpactsReport2
    implements ValidationReport
{

    private final Logger logger = new Logger( getClass() );

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        final Set<ProjectVersionRef> missing = new HashSet<ProjectVersionRef>();
        for ( final ProjectVersionRef ref : session.getMissing() )
        {
            if ( ref instanceof ArtifactRef )
            {
                missing.add( ( (ArtifactRef) ref ).asProjectVersionRef() );
            }
            else
            {
                missing.add( ref );
            }
        }

        final List<ProjectVersionRef> sortedMissing = new ArrayList<ProjectVersionRef>( missing );
        Collections.sort( sortedMissing, new ToStringComparator<ProjectVersionRef>() );

        final EProjectWeb web = session.getProjectWeb();
        PrintWriter writer = null;

        try
        {
            writer = session.getReportWriter( this );
            logger.info( "Looking for impact of missing projects:\n  %s\n", join( missing, "\n  " ) );
            for ( final ProjectVersionRef missingRef : sortedMissing )
            {
                calculateMissingImpacts( missingRef, web, writer );
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    private void calculateMissingImpacts( final ProjectVersionRef missingRef, final EProjectWeb web,
                                          final PrintWriter writer )
    {
        logger.info( "Generating impacted-project list for: %s", missingRef );

        final Set<List<ProjectRelationship<?>>> paths = web.getPathsTo( missingRef );

        final Set<ProjectVersionRef> allImpacted = new HashSet<ProjectVersionRef>();
        for ( final List<ProjectRelationship<?>> path : paths )
        {
            for ( final ProjectRelationship<?> rel : path )
            {
                final ProjectVersionRef impacted = rel.getDeclaring()
                                                      .asProjectVersionRef();
                if ( !impacted.equals( missingRef ) )
                {
                    logger.info( "  +%s", impacted );
                    allImpacted.add( impacted );
                }
            }
        }

        if ( allImpacted.isEmpty() )
        {
            return;
        }

        logger.info( "Sorting list of %d impacted projects for: %s", allImpacted.size(), missingRef );
        final List<ProjectVersionRef> allImpactedList = new ArrayList<ProjectVersionRef>( allImpacted );
        Collections.sort( allImpactedList, new ToStringComparator<ProjectVersionRef>() );

        writer.printf( "\n\n%s:\n----------------------------------------------\n\n  %s", missingRef,
                       join( allImpactedList, "\n  " ) );
    }

}
