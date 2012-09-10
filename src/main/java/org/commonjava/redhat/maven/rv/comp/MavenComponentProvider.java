package org.commonjava.redhat.maven.rv.comp;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Singleton
@Component( role = MavenComponentProvider.class )
public class MavenComponentProvider
    extends AbstractMAEApplication
{

    @Requirement
    private ModelBuilder modelBuilder;

    @Requirement
    private RepositorySystem repoSystem;

    @Requirement
    private SessionInitializer sessionInitializer;

    @PostConstruct
    public void initialize()
        throws MAEException
    {
        load();
    }

    @Produces
    public SessionInitializer getSessionInitializer()
    {
        return sessionInitializer;
    }

    @Produces
    public RepositorySystem getRepositorySystem()
    {
        return repoSystem;
    }

    @Produces
    public ModelBuilder getModelBuilder()
    {
        return modelBuilder;
    }

    public String getId()
    {
        return "Repository-Validator-Components";
    }

    public String getName()
    {
        return getId();
    }

}
