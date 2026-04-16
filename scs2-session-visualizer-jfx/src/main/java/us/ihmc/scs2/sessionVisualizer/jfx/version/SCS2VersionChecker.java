package us.ihmc.scs2.sessionVisualizer.jfx.version;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * This class is used to check if the current version of SCS2 is the latest one.
 * <p>
 * The current version is retrieved from the MANIFEST.MF file.
 * </p>
 * <p>
 * The latest version is retrieved from the GitHub API.
 * </p>
 */
public class SCS2VersionChecker
{
   /**
    * The current version of SCS2.
    */
   private static String CURRENT_BASE_VERSION;
   /**
    * The latest release of SCS2 with its version and URL.
    */
   private static Release LATEST_RELEASE;
   /**
    * The latest version of SCS2.
    */
   private static String LATEST_BASE_VERSION;

   /**
    * The URL to the GitHub API to retrieve the latest release of SCS2.
    */
   private static final URL REPOSITORY_API_URL;

   public static final URL REPOSITORY_URL;
   public static final URL DOWNLOAD_URL;
   public static final URL UPSTREAM_REPOSITORY_URL;

   static
   {
      try
      {
         REPOSITORY_API_URL = new URL("https://api.github.com/repos/seabasstea/simulation-construction-set-2/releases/latest");
         REPOSITORY_URL = new URL("https://www.github.com/seabasstea/simulation-construction-set-2");
         DOWNLOAD_URL = new URL("https://github.com/seabasstea/simulation-construction-set-2/releases/latest");
         UPSTREAM_REPOSITORY_URL = new URL("https://www.github.com/ihmcrobotics/simulation-construction-set-2");
      }
      catch (MalformedURLException e)
      {
         throw new RuntimeException(e);
      }
   }

   /**
    * Returns the current version of SCS2.
    * <p>
    * The current version is retrieved from the MANIFEST.MF file.
    * </p>
    *
    * @return the current version of SCS2.
    */
   public static String getCurrentBaseVersion()
   {
      if (CURRENT_BASE_VERSION == null)
      {
         String version = SCS2VersionChecker.class.getPackage().getImplementationVersion();
         CURRENT_BASE_VERSION = version == null ? "[source-code-version]" : toBaseVersion(version);
      }
      return CURRENT_BASE_VERSION;
   }

   /**
    * Returns whether the application is running from source code (not from an installed package).
    *
    * @return {@code true} if running from source, {@code false} if running from an installed package.
    */
   public static boolean isRunningFromSource()
   {
      return "[source-code-version]".equals(getCurrentBaseVersion());
   }

   /**
    * Returns the latest release of SCS2 with its version and URL.
    * <p>
    * The latest release is retrieved from the GitHub API.
    * </p>
    *
    * @return the latest release of SCS2.
    */
   public static Release getLatestRelease()
   {
      if (LATEST_RELEASE == null)
      {
         OkHttpClient client = new OkHttpClient();
         Request request = new Request.Builder().url(REPOSITORY_API_URL).build();

         try (Response response = client.newCall(request).execute())
         {
            if (!response.isSuccessful())
               throw new IOException("Unexpected code " + response);

            Gson gson = new Gson();
            LATEST_RELEASE = gson.fromJson(response.body().string(), Release.class);
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
      }
      return LATEST_RELEASE;
   }

   /**
    * Returns the latest version of SCS2.
    * <p>
    * The latest version is retrieved from the GitHub API.
    * </p>
    *
    * @return the latest version of SCS2.
    */
   public static String getLatestBaseVersion()
   {
      if (LATEST_BASE_VERSION == null)
      {
         Release release = getLatestRelease();
         if (release != null && release.tag_name != null)
            LATEST_BASE_VERSION = toBaseVersion(release.tag_name);
      }
      return LATEST_BASE_VERSION;
   }

   public static String getLatestReleaseURL()
   {
      return getLatestRelease().html_url;
   }

   /**
    * Returns the URL of the latest MSI asset from the GitHub release, or {@code null} if not found.
    */
   public static String getLatestMsiAssetUrl()
   {
      Release release = getLatestRelease();
      if (release == null || release.assets == null)
         return null;

      for (Asset asset : release.assets)
      {
         if (asset.name != null && asset.name.toLowerCase().endsWith(".msi"))
            return asset.browser_download_url;
      }
      return null;
   }

   /**
    * Clears the cached release data so the next call to {@link #getLatestRelease()} fetches fresh data.
    */
   public static void resetCache()
   {
      LATEST_RELEASE = null;
      LATEST_BASE_VERSION = null;
   }

   /**
    * Returns whether the current version of SCS2 is the latest one.
    * <p>
    * Uses numeric version comparison (e.g. 0.33.0 &gt; 0.32.1).
    * </p>
    *
    * @return {@code true} if the current version is greater than or equal to the latest, {@code false} otherwise.
    */
   public static boolean isLatestRelease()
   {
      String current = getCurrentBaseVersion();
      String latest = getLatestBaseVersion();
      if (current == null || latest == null)
         return true; // assume up-to-date if we can't check
      return compareVersions(current, latest) >= 0;
   }

   /**
    * Compares two version strings numerically (e.g. "0.33.0" vs "0.32.1").
    *
    * @return positive if a &gt; b, negative if a &lt; b, zero if equal.
    */
   static int compareVersions(String a, String b)
   {
      String[] partsA = a.split("\\.");
      String[] partsB = b.split("\\.");
      int length = Math.max(partsA.length, partsB.length);

      for (int i = 0; i < length; i++)
      {
         int numA = i < partsA.length ? parseSegment(partsA[i]) : 0;
         int numB = i < partsB.length ? parseSegment(partsB[i]) : 0;
         if (numA != numB)
            return numA - numB;
      }
      return 0;
   }

   private static int parseSegment(String segment)
   {
      try
      {
         return Integer.parseInt(segment.trim());
      }
      catch (NumberFormatException e)
      {
         return 0;
      }
   }

   public static class Asset
   {
      public String name;
      public String browser_download_url;
   }

   public static class Release
   {
      private String tag_name;
      private String html_url;
      private List<Asset> assets;
   }

   /**
    * Converts a version string to its base version.
    * <p>
    * For example, {@code "17-0.0.1"} is converted to {@code "0.0.1"}, and {@code "v17-0.0.1"} is also converted to {@code "0.0.1"}.
    * </p>
    *
    * @param version the version to convert.
    * @return the base version.
    */
   private static String toBaseVersion(String version)
   {
      String result = version.trim();
      if (result.startsWith("v"))
         result = result.substring(1);
      result = result.replace("17-", "").replace("-java-17", "");
      return result;
   }
}
