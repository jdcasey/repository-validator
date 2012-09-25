package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.PluginRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;
import org.commonjava.util.logging.Logger;

import edu.uci.ics.jung.graph.DirectedGraph;

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
        final DirectedGraph<ProjectVersionRef, ProjectRelationship<?>> graph = web.getRawGraph();
        PrintWriter writer = null;

        try
        {
            writer = session.getReportWriter( this );
            logger.info( "Looking for impact of missing projects:\n  %s\n", join( missing, "\n  " ) );
            for ( final ProjectVersionRef missingRef : sortedMissing )
            {
                calculateMissingImpacts( missingRef, graph, writer );
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    private void calculateMissingImpacts( final ProjectVersionRef missingRef,
                                          final DirectedGraph<ProjectVersionRef, ProjectRelationship<?>> graph,
                                          final PrintWriter writer )
    {
        logger.info( "Generating impacted-project list for: %s", missingRef );

        final Set<ProjectVersionRef> allImpacted = new HashSet<ProjectVersionRef>();
        final Set<ProjectVersionRef> nextImpacted = new HashSet<ProjectVersionRef>();
        nextImpacted.add( missingRef );

        int idx = 0;
        do
        {
            logger.info( "PASS: %d", idx );
            final Set<ProjectVersionRef> current = new HashSet<ProjectVersionRef>( nextImpacted );

            nextImpacted.clear();

            for ( final ProjectVersionRef ref : current )
            {
                if ( !missingRef.equals( ref ) )
                {
                    logger.info( "  +%s", ref );
                    allImpacted.add( ref );
                }

                final Collection<ProjectRelationship<?>> inEdges = graph.getInEdges( ref );
                if ( inEdges != null && !inEdges.isEmpty() )
                {
                    for ( final ProjectRelationship<?> rel : inEdges )
                    {
                        if ( rel instanceof DependencyRelationship
                            && ( ( (DependencyRelationship) rel ).isManaged()
                                || ( (DependencyRelationship) rel ).getTarget()
                                                                   .isOptional() || !DependencyScope.runtime.implies( ( (DependencyRelationship) rel ).getScope() ) ) )
                        {
                            continue;
                        }

                        if ( rel instanceof PluginRelationship && ( (PluginRelationship) rel ).isManaged() )
                        {
                            continue;
                        }

                        logger.info( "NEXT++ %s", rel.getDeclaring() );
                        nextImpacted.add( rel.getDeclaring() );
                    }
                }
            }

            logger.info( "    ...rendered %d new impacts to analyze.", nextImpacted.size() );
            idx++;
        }
        while ( !nextImpacted.isEmpty() );

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
