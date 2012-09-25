package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
import org.apache.maven.graph.effective.rel.RelationshipComparator;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;
import org.commonjava.util.logging.Logger;

import edu.uci.ics.jung.graph.DirectedGraph;

@Named( "missing-impacts-3.txt" )
public class MissingImpactsReport3
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
                final List<List<ProjectRelationship<?>>> paths = new PathFinder( web, missingRef ).getPaths();
                for ( final Iterator<List<ProjectRelationship<?>>> iterator = paths.iterator(); iterator.hasNext(); )
                {
                    final List<ProjectRelationship<?>> path = iterator.next();
                    if ( path.isEmpty() )
                    {
                        iterator.remove();
                    }
                }

                if ( paths.isEmpty() )
                {
                    continue;
                }

                writer.printf( "%s (%d paths):\n-------------------------------------------------------------------\n",
                               missingRef, paths.size() );

                Collections.sort( paths, new PathRootComparator() );

                int idx = 0;
                for ( final List<ProjectRelationship<?>> path : paths )
                {
                    if ( path.isEmpty() )
                    {
                        continue;
                    }

                    final ProjectVersionRef declaring = path.get( 0 )
                                                            .getDeclaring();

                    final StringBuilder spacing = new StringBuilder();
                    if ( path.size() > 9 )
                    {
                        spacing.append( " " );
                    }

                    if ( path.size() > 99 )
                    {
                        spacing.append( " " );
                    }

                    // if there are > 999 links in the path, to hell with it, we'll tolerate a little formatting glitch!

                    writer.printf( "\n  %d.%s %s\n%s       %s\n", idx, spacing, declaring, spacing,
                                   join( path, "\n       " + spacing ) );
                    idx++;
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

    private static final class PathFinder
    {

        private final ProjectVersionRef target;

        private final DirectedGraph<ProjectVersionRef, ProjectRelationship<?>> graph;

        private final List<List<ProjectRelationship<?>>> paths = new ArrayList<List<ProjectRelationship<?>>>();

        public PathFinder( final EProjectWeb web, final ProjectVersionRef target )
        {
            this.graph = web.getRawGraph();
            if ( target instanceof ArtifactRef )
            {
                this.target = ( (ArtifactRef) target ).asProjectVersionRef();
            }
            else
            {
                this.target = target;
            }
        }

        public synchronized List<List<ProjectRelationship<?>>> getPaths()
        {
            if ( paths.isEmpty() )
            {
                recurseToRoot( target, new ArrayList<ProjectRelationship<?>>() );
            }
            return paths;
        }

        private void recurseToRoot( final ProjectVersionRef declaring, final List<ProjectRelationship<?>> inPath )
        {
            final Collection<ProjectRelationship<?>> edges = graph.getInEdges( declaring );
            if ( edges == null || edges.isEmpty() )
            {
                paths.add( inPath );
                return;
            }

            for ( final ProjectRelationship<?> rel : edges )
            {
                if ( rel instanceof DependencyRelationship
                    && ( ( (DependencyRelationship) rel ).isManaged() || ( (DependencyRelationship) rel ).getTarget()
                                                                                                         .isOptional() || !DependencyScope.runtime.implies( ( (DependencyRelationship) rel ).getScope() ) ) )
                {
                    continue;
                }

                if ( rel instanceof PluginRelationship && ( (PluginRelationship) rel ).isManaged() )
                {
                    continue;
                }

                final ProjectVersionRef decl = rel.getDeclaring();

                final List<ProjectRelationship<?>> currentPath = new ArrayList<ProjectRelationship<?>>( inPath );
                currentPath.add( rel );

                recurseToRoot( decl, currentPath );
            }

        }
    }

    private static final class PathRootComparator
        implements Comparator<List<ProjectRelationship<?>>>
    {
        private final ToStringComparator<ProjectVersionRef> refComp = new ToStringComparator<ProjectVersionRef>();

        private final RelationshipComparator relComp = new RelationshipComparator();

        public int compare( final List<ProjectRelationship<?>> first, final List<ProjectRelationship<?>> second )
        {
            if ( first.isEmpty() && second.isEmpty() )
            {
                return 0;
            }
            else if ( first.isEmpty() && !second.isEmpty() )
            {
                return 1;
            }
            else if ( !first.isEmpty() && second.isEmpty() )
            {
                return -1;
            }

            final ProjectRelationship<?> firstRoot = first.get( 0 );
            final ProjectRelationship<?> secondRoot = second.get( 0 );

            final ProjectVersionRef firstDecl = firstRoot.getDeclaring();
            final ProjectVersionRef secondDecl = secondRoot.getDeclaring();

            int comp = refComp.compare( firstDecl, secondDecl );
            if ( comp == 0 )
            {
                comp = relComp.compare( firstRoot, secondRoot );
            }

            return comp;
        }

    }

}
