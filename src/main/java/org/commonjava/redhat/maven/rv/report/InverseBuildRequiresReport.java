package org.commonjava.redhat.maven.rv.report;

import static org.apache.maven.graph.common.DependencyScope.provided;
import static org.apache.maven.graph.common.DependencyScope.test;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "inverse-build-requires.txt" )
public class InverseBuildRequiresReport
    extends AbstractInverseRelationshipReport
{

    @Override
    protected Set<ProjectVersionRef> getReferencesToReport( final ValidatorSession session )
    {
        return session.getSeen();
    }

    @Override
    protected Set<ProjectRelationship<?>> filterRelationships( final Set<ProjectRelationship<?>> rels )
    {
        final Set<ProjectRelationship<?>> result = new HashSet<ProjectRelationship<?>>();
        for ( final ProjectRelationship<?> rel : rels )
        {
            if ( rel instanceof DependencyRelationship )
            {
                final DependencyScope scope = ( (DependencyRelationship) rel ).getScope();
                if ( scope == provided || scope == test || ( (DependencyRelationship) rel ).getTarget()
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

}
