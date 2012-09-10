package org.commonjava.redhat.maven.rv.validator;

import org.apache.maven.project.MavenProject;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

public interface RepoValidator
{

    boolean isValid( MavenProject project, ValidatorSession session );

}
