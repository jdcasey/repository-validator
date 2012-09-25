package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.EProjectWeb;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.apache.maven.graph.effective.rel.RelationshipComparator;
import org.apache.maven.graph.effective.traverse.ImpactTraversal;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.commonjava.redhat.maven.rv.util.ToStringComparator;
import org.commonjava.util.logging.Logger;

@Named( "missing-impacts.txt" )
public class MissingImpactsReport
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

        logger.info( "Looking for impact of missing projects:\n  %s\n", join( missing, "\n  " ) );

        final EProjectWeb projectWeb = session.getProjectWeb();
        final ImpactTraversal traversal = new ImpactTraversal( missing );

        final Set<ProjectVersionRef> roots = projectWeb.getRoots();
        for ( final ProjectVersionRef root : roots )
        {
            logger.info( "Starting traversal of root: %s for impacts of target(s)\n", root );
            projectWeb.traverse( root, traversal );
        }

        final Map<ProjectVersionRef, Set<List<ProjectRelationship<?>>>> impactedPaths = traversal.getImpactedPaths();

        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            // write impacts report, organized by missing target, then by project at root of path, with list of paths between them.
            for ( final Map.Entry<ProjectVersionRef, Set<List<ProjectRelationship<?>>>> pathsEntry : impactedPaths.entrySet() )
            {
                final ProjectVersionRef target = pathsEntry.getKey();
                final List<List<ProjectRelationship<?>>> paths =
                    new ArrayList<List<ProjectRelationship<?>>>( pathsEntry.getValue() );
                if ( paths.isEmpty() )
                {
                    continue;
                }

                writer.printf( "%s:\n---------------------------\n", target );

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

                if ( idx == 0 )
                {
                    writer.println( "  -NONE-" );
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
