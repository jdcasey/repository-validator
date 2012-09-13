package org.commonjava.redhat.maven.rv.report;

import static org.apache.maven.graph.common.DependencyScope.runtime;
import static org.apache.maven.graph.common.DependencyScope.test;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.apache.maven.graph.effective.rel.RelationshipComparator;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "build-requires.txt" )
public class BuildRequiresReport
    extends AbstractRelationshipReport<ProjectRelationship<?>>
{

    @Override
    protected void print( final ProjectRelationship<?> rel, final PrintWriter writer, final ValidatorSession session )
    {
        if ( rel instanceof DependencyRelationship )
        {
            final DependencyRelationship dr = (DependencyRelationship) rel;
            final ArtifactRef target = dr.getTarget();
            final boolean pomMissing = session.isMissing( target.asProjectVersionRef() );
            final boolean artMissing = session.isMissing( target );

            writer.printf( "\n  %d. %s (type: dependency, scope: %s)\n    POM:      %s\n    Artifact: %s",
                           rel.getIndex(), target, dr.getScope(), ( pomMissing ? "MISSING" : "OK" ),
                           ( artMissing ? "MISSING" : "OK" ) );
        }
        else
        {
            final ProjectVersionRef target = rel.getTarget();
            final boolean missing = session.isMissing( target );

            writer.printf( "\n  %d. %s (type: %s)\n    POM:      %s", rel.getIndex(), target, rel.getType()
                                                                                                 .name()
                                                                                                 .toLowerCase(),
                           ( missing ? "MISSING" : "OK" ) );
        }
    }

    @Override
    protected Set<ProjectVersionRef> getProjectReferences( final ValidatorSession session )
    {
        return session.getBoms();
    }

    @Override
    protected Set<ProjectRelationship<?>> filter( final Set<ProjectRelationship<?>> rels )
    {
        final Set<ProjectRelationship<?>> result = new HashSet<ProjectRelationship<?>>();
        for ( final ProjectRelationship<?> rel : rels )
        {
            if ( rel instanceof DependencyRelationship )
            {
                final DependencyRelationship dr = (DependencyRelationship) rel;
                final DependencyScope scope = dr.getScope();
                if ( ( test.implies( scope ) && !runtime.implies( scope ) ) || dr.getTarget()
                                                                                 .isOptional() )
                {
                    result.add( rel );
                }
            }
            else
            {
                result.add( rel );
            }
        }

        return result;
    }

    @Override
    protected List<ProjectRelationship<?>> sort( final Set<ProjectRelationship<?>> filtered )
    {
        final List<ProjectRelationship<?>> sorted = new ArrayList<ProjectRelationship<?>>( filtered );
        Collections.sort( sorted, new RelationshipComparator() );

        return sorted;
    }

}
