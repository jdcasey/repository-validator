package org.commonjava.redhat.maven.rv.report;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.apache.maven.graph.effective.rel.RelationshipComparator;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "boms.txt" )
public class BomsReport
    extends AbstractRelationshipReport<DependencyRelationship>
{

    @Override
    protected void print( final DependencyRelationship rel, final PrintWriter writer, final ValidatorSession session )
    {
        final ArtifactRef target = rel.getTarget();
        final boolean pomMissing = session.isMissing( target.asProjectVersionRef() );
        final boolean artMissing = session.isMissing( target );

        writer.printf( "\n  %d. %s (scope: %s)\n    POM:      %s\n    Artifact: %s", rel.getIndex(), target,
                       rel.getScope(), ( pomMissing ? "MISSING" : "OK" ), ( artMissing ? "MISSING" : "OK" ) );
    }

    @Override
    protected Set<ProjectVersionRef> getProjectReferences( final ValidatorSession session )
    {
        return session.getBoms();
    }

    @Override
    protected Set<DependencyRelationship> filter( final Set<ProjectRelationship<?>> rels )
    {
        final Set<DependencyRelationship> filtered = new HashSet<DependencyRelationship>();

        for ( final ProjectRelationship<?> rel : rels )
        {
            if ( rel instanceof DependencyRelationship )
            {
                final DependencyRelationship dr = (DependencyRelationship) rel;
                if ( dr.isManaged() )
                {
                    filtered.add( dr );
                }
            }
        }
        return filtered;
    }

    @Override
    protected List<DependencyRelationship> sort( final Set<DependencyRelationship> filtered )
    {
        final List<DependencyRelationship> sorted = new ArrayList<DependencyRelationship>( filtered );
        Collections.sort( sorted, new RelationshipComparator() );

        return sorted;
    }

}
