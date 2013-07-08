package org.commonjava.redhat.maven.rv.comp;

import java.io.File;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.session.ProjectToolsSession;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;

@Singleton
public class MavenComponentManager
{

    private MAEApp app;

    @PostConstruct
    public void initialize()
        throws MAEException
    {
        app = new MAEApp();
        app.load();
    }

    @Produces
    public RepositoryMetadataManager getRepositoryMetadataManager()
    {
        return app.repositoryMetadataManager;
    }

    @Produces
    public PluginVersionResolver getPluginVersionResolver()
    {
        return app.pluginVersionResolver;
    }

    @Produces
    public SessionInitializer getSessionInitializer()
    {
        return app.sessionInitializer;
    }

    @Produces
    public RepositorySystem getRepositorySystem()
    {
        return app.repoSystem;
    }

    @Produces
    public ModelBuilder getModelBuilder()
    {
        return app.modelBuilder;
    }

    @Produces
    public ArtifactResolver getArtifactResolver()
    {
        return app.resolver;
    }

    @Produces
    public RemoteRepositoryManager getRemoteRepositoryManager()
    {
        return app.repoManager;
    }

    @Component( role = MAEApp.class )
    private static class MAEApp
        extends AbstractMAEApplication
    {

        @Requirement
        private ModelBuilder modelBuilder;

        @Requirement
        private RepositorySystem repoSystem;

        @Requirement
        private PluginVersionResolver pluginVersionResolver;

        @Requirement
        private RepositoryMetadataManager repositoryMetadataManager;

        @Requirement
        private SessionInitializer sessionInitializer;

        @Requirement
        private SettingsBuilder settingsBuilder;

        @Requirement
        private MavenExecutionRequestPopulator requestPopulator;

        @Requirement
        private RemoteRepositoryManager repoManager;

        @Requirement
        private ArtifactResolver resolver;

        public String getId()
        {
            return "Repository-Validator-Components";
        }

        public String getName()
        {
            return getId();
        }
    }

    public void initializeSessionComponents( final ProjectToolsSession projectSession )
        throws ProjectToolsException
    {
        app.sessionInitializer.initializeSessionComponents( projectSession );
    }

    public ArtifactRepository createLocalRepository( final File localRepo )
        throws InvalidRepositoryException
    {
        return app.repoSystem.createLocalRepository( localRepo );
    }

    public void configureFromSettings( final ProjectToolsSession session, final File settingsXml )
        throws SettingsBuildingException, MavenExecutionRequestPopulationException
    {
        final DefaultSettingsBuildingRequest req = new DefaultSettingsBuildingRequest();
        req.setUserSettingsFile( settingsXml );
        req.setSystemProperties( System.getProperties() );

        MavenExecutionRequest executionRequest = session.getExecutionRequest();
        final SettingsBuildingResult result = app.settingsBuilder.build( req );
        final Settings settings = result.getEffectiveSettings();

        executionRequest = app.requestPopulator.populateFromSettings( executionRequest, settings );
        session.setExecutionRequest( executionRequest );
    }

}
