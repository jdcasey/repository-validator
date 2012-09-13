package org.commonjava.redhat.maven.rv.report;

import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "inverse-missing-requires.txt" )
public class InverseMissingRequiresReport
    extends InverseRequiresReport
{

    @Override
    protected Set<ProjectVersionRef> getReferencesToReport( final ValidatorSession session )
    {
        return session.getMissing();
    }

}
