#README: Repository Validator

Repository Validator is a tool used to examine the internal consistency of a Maven artifact repository, and generate reports based on the health of the artifacts it finds there. It is meant to review repositories that are supposed to be self-contained, which means it looks for dangling references to POMs that aren't in the repository. It also looks for any problems related to Maven's consumption of the POMs, like dependencies without versions, etc. As it checks the POMs in a repository, Repository Validator also builds up a map of relationships between these POMs, which allows the reports to generate lists of POMs impacted by a given missing dependency, among other things.


##Dependencies

Mostly what you would find on the central Maven repository (http://repo.maven.apache.org/maven2/), with an extra as-yet unreleased dependency:

* **Maven Atlas** - [http://github.com/jdcasey/maven-atlas/](http://github.com/jdcasey/maven-atlas/)


##Building

Simple:

1. In `maven-atlas/` execute `mvn clean install`
2. In `repository-validator/` execute `mvn clean package`

Once built, the Repository Validator executable distribution is available in three formats:

* tar.bz2 (Bzipped Tarball)
* zip
* directory

The directory assembly is the simplest way to execute the validator after building it. Simply execute:

    target/repository-validator-$VERSION/repository-validator-$COMMIT_ID/bin/rv.sh <options>


##Usage

To get the version, try: `/path/to/repository-validator/bin/rv.sh -v`

To get help, try: `/path/to/repository-validator/bin/rv.sh -h`

To run against a given repository directory, try: `/path/to/repository-validator/bin/rv.sh /path/to/the/repository/dir/`


##Reports

###Reporting Output

All reports are available in the `${user.dir}/rv-workspace/reports/` directory.

###Per-Project Validity reports
  
  These reports list all relationships of a certain type on a per-project basis, and include status about each relationship (whether the POM was missing, whether the artifact was resolved, if there is one).
  
  They include:
  
* **Requirements** - These are runtime requirements including runtime-scoped (or implied runtime-scoped) dependencies and parent POMs

* **Build Requirements** - Anything required to build the project EXCEPT for those things included in the Requirements report

* **BOMs** - Bill-of-Materials POMs referenced by the POMs in the repository, with status on each MANAGED dependency they provide

###Inverted Per-Project Validity reports

  These reports list all relationships that depend ON a given project (the users of the project, effectively), and include the type of relationship and details pertinent to that relationship (scope, for dependencies, etc.). They are called 'inverted' since they list by usage rather than by declaration.
  
  They include:
  
* **Inverted Requirements** - Lists all projects that have a runtime requirement on the project in question, and the nature of that requirement

* **Inverted Build Requirements** - Lists all projects that have a build-time requirement on the project in question, and the nature of that requirement. This report EXCLUDES any information that overlaps the 'Inverted Requirements' report.

* **Inverted Missing Requirements** - For all projects listed as MISSING (unresolvable, or unreadable due to a parsing error), list all projects that have a runtime requirement on this project.

* **Inverted Build Requirements** - Same as 'Inverted Build Requirements' except that, as with 'Inverted Missing Requirements', it only lists projects that failed to build / resolve.
  
###Project-List reports
  
  These are reports that list all the projects that fall into a given category.
  
  They include:
  
* **Processed Projects** - All POMs visited by the validator, whether or not they were resolvable / buildable

* **Missing Projects** - All POMs that could not be resolved / parsed by the validator. For unresolvable projects, they must have been referenced by another project, since the validator's point of entry is a file-scan of the repository that looks for POM files.

* **Valid Projects** - Processed - Missing == Projects that were resolvable and parsable (and referenced or present in the file scan).

###Other reports

* **Model Errors & Failures** - For each MISSING / broken POM, list the errors and parsing / validation problems that came up when the validator tried to load it.

