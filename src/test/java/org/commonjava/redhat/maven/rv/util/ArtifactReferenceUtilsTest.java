package org.commonjava.redhat.maven.rv.util;

import static org.commonjava.redhat.maven.rv.util.ArtifactReferenceUtils.toArtifactRef;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.apache.maven.graph.common.ref.ArtifactRef;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ArtifactReferenceUtilsTest
{

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void addErrorForDependencyWithUnresolvableVersionExpression_DoNotFail()
        throws Exception
    {
        final File repo = temp.newFolder();
        final File ws = temp.newFolder();

        final ValidatorSession session = new ValidatorSession.Builder( repo, ws ).build();

        final Model model = new Model();
        model.setModelVersion( "4.0.0" );
        model.setGroupId( "group.id" );
        model.setArtifactId( "artifact-id" );
        model.setVersion( "1.0" );

        final ProjectVersionRef ref =
            new ProjectVersionRef( model.getGroupId(), model.getArtifactId(), model.getVersion() );

        final Dependency dep = new Dependency();
        dep.setGroupId( "other.group.id" );
        dep.setArtifactId( "dep-artifact" );
        dep.setVersion( "${version.missing}" );

        final ArtifactRef artifactRef = toArtifactRef( dep, ref, session, model );

        assertThat( artifactRef, nullValue() );
    }

}
