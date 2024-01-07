package coursier.tests

import coursier.LocalRepositories
import coursier.Repositories
import coursier.Resolve
import coursier.core.Activation
import coursier.core.Configuration
import coursier.core.Extension
import coursier.core.Reconciliation
import coursier.error.ResolutionError
import coursier.ivy.IvyRepository
import coursier.ivy.Pattern
import coursier.ivy.PropertiesPattern
import coursier.maven.MavenRepository
import coursier.params.MavenMirror
import coursier.params.Mirror
import coursier.params.ResolutionParams
import coursier.params.TreeMirror
import coursier.util.ModuleMatchers
import utest._

import java.nio.file.Paths
import scala.async.Async.async
import scala.async.Async.await
import scala.collection.compat._

object ResolveConfigTests extends TestSuite {
  import TestHelpers.{ec, cache, maybeWriteTextResource, handmadeMetadataBase}

  private val resolve = Resolve()
    .noMirrors
    .withCache(cache)
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          Activation.Os(
            Some("x86_64"),
            Set("mac", "unix"),
            Some("mac os x"),
            Some("10.15.1")
          )
        }
        .withJdkVersion("1.8.0_121")
    )

  val tests = Tests {
    test("repositories from config") {
      val configFileName = handmadeMetadataBase + "/resolveTest/config/config.json"
      val sonatypeRepo   = "releases"
      val mavenRepo      = "https://some-artifactory.io/artifactory/some-repo"
      val privateIvyRepoPattern =
        Pattern.default.addPrefix("https://some-artifactory.io/artifactory/some-ivy-repo/")
      maybeWriteTextResource(
        configFileName,
        s"""|{
            |  "repositories": {
            |    "default": [
            |      "ivy2Local",
            |      "m2local",
            |      "central",
            |      "sonatype:$sonatypeRepo",
            |      "$mavenRepo",
            |      "ivy:${privateIvyRepoPattern.string}"
            |    ]
            |  }
            |}""".stripMargin
      )
      val resolveWithDefaultRepositories =
        resolve.withConfFiles(Seq(Paths.get(configFileName).asInstanceOf[Resolve.Path]))
      "the default repositories should be used" - async {
        val res = await(resolve.finalRepositories.future())
        assert(res == Resolve.defaultRepositories)
      }
      "config file repositories are used" - async {
        val res = await(resolveWithDefaultRepositories.finalRepositories.future())
        val expectedRepositories = Seq(
          LocalRepositories.ivy2Local,
          LocalRepositories.Dangerous.maven2Local,
          Repositories.central,
          Repositories.sonatype(sonatypeRepo),
          MavenRepository(mavenRepo),
          IvyRepository(privateIvyRepoPattern)
        )
        assert(res == expectedRepositories)
      }
    }
  }
}
