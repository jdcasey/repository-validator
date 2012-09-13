package org.commonjava.redhat.maven.rv.report;

import static org.apache.maven.graph.common.DependencyScope.compile;
import static org.apache.maven.graph.common.DependencyScope.runtime;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.DependencyScope;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.effective.rel.DependencyRelationship;
import org.apache.maven.graph.effective.rel.ParentRelationship;
import org.apache.maven.graph.effective.rel.ProjectRelationship;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "inverse-requires.txt" )
public class InverseRequiresReport
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
                if ( ( scope == runtime || scope == compile ) && !( (DependencyRelationship) rel ).getTarget()
                                                                                                  .isOptional() )
                {
                    result.add( rel );
                }
            }
            else if ( rel instanceof ParentRelationship )
            {
                result.add( rel );
            }
        }

        return result;
    }

}
