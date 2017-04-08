package net.minecraftforge.mercurius.updater;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Utils
{
    private static final String FORGE_MAVEN = "http://files.minecraftforge.net/maven/"; //TODO: HTTPS
    private static final int BUFFER_SIZE = 4096;
    private static final int TIMEOUT = 24 * 60 * 60 * 1000;
    private static final String FORGEFINGERPRINT = "E3:C3:D5:0C:7C:98:6D:F7:4C:64:5C:0A:C5:46:39:74:1C:90:A5:57".toLowerCase().replace(":", "");

    public static String downloadFile(String url, File target)
    {
        int response = -1;
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection)(new URL(url)).openConnection();
            response = conn.getResponseCode();
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            LogHelper.warn("Failed to download " + url + " with exception: " + e.getMessage());
            return null;
        }

        if (response == HttpURLConnection.HTTP_OK)
        {
            if (!target.getParentFile().exists())
                target.getParentFile().mkdirs();

            FileOutputStream output = null;
            DigestInputStream input = null;

            try
            {
                input = new DigestInputStream(conn.getInputStream(), MessageDigest.getInstance("SHA1"));
                output = new FileOutputStream(target);

                int bytesRead = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = input.read(buffer)) != -1)
                {
                    output.write(buffer, 0, bytesRead);
                }
                return bytesToHex(input.getMessageDigest().digest());
            }
            catch (IOException e)
            {
                e.printStackTrace(); // Something broke so lets close and delete the temp file
                LogHelper.warn("Failed to download " + url + " with exception: " + e.getMessage());
                closeSilently(output);
                if (target.exists())
                    target.delete();
            }
            catch (NoSuchAlgorithmException e){} // Should never happen, seriously what Java install doesn't have SHA1?
            finally
            {
                closeSilently(input);
                closeSilently(output);
            }
        }
        else
        {
            LogHelper.warn("No file to download. Server replied HTTP code: " + response);
        }
        conn.disconnect();
        return null;
    }

    public static String downloadString(String url)
    {
        try
        {
            HttpURLConnection conn = (HttpURLConnection)((new URL(url)).openConnection());
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            return toString(conn.getInputStream());
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public static String readFile(File file)
    {
        try
        {
            return toString(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean writeFile(File file, String data)
    {
        BufferedWriter out = null;
        try
        {
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();

            out = new BufferedWriter(new FileWriter(file));
            out.write(data);
            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace(); //Something went wrong writing to the file? How?
        }
        finally
        {
            closeSilently(out);
        }
        return false;
    }

    private static String toString(InputStream in)
    {
        try
        {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = in.read(buffer)) != -1)
            {
                result.write(buffer, 0, length);
            }
            return result.toString("UTF-8");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            closeSilently(in);
        }
        return null;
    }

    private static void closeSilently(Closeable c)
    {
        if (c != null)
        {
            try
            {
                c.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private static void closeSilently(JarFile c)
    {
        if (c != null)
        {
            try
            {
                c.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    final static protected char[] hexArray = "0123456789abcdef".toCharArray();
    private static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++)
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static File getMavenFile(File root, String group, String artifact, String version)
    {
        String path = group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + ".jar";
        return new File(root, path);
    }

    public static File findFirstParent(File file, String target)
    {
        for (; file != null && !file.getName().equals(target); file = file.getParentFile());
        return file;
    }

    public static File getJar(Class<?> cls) throws Exception
    {
        URL url = cls.getProtectionDomain().getCodeSource().getLocation();
        String extURL = url.toExternalForm();

        if (!extURL.endsWith(".jar"))
        {
            String suffix = "/" + (cls.getName()).replace(".", "/") + ".class";
            extURL = extURL.replace(suffix, "");
            if (extURL.startsWith("jar:") && extURL.endsWith(".jar!"))
                extURL = extURL.substring(4, extURL.length() - 1);
        }

        try {
            return new File(new URL(extURL).toURI());
        } catch(URISyntaxException ex) {
            return new File(new URL(extURL).getPath());
        }
    }


    public static String getSHA1(File file)
    {
        byte[] buffer = new byte[BUFFER_SIZE];
        DigestInputStream input = null;
        try
        {
            input = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("SHA1"));
            while (input.read(buffer) > 0); //Read everything!
            return Utils.bytesToHex(input.getMessageDigest().digest());
        }
        catch (NoSuchAlgorithmException e){} // Should never happen, seriously what Java install doesn't have SHA1?
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            closeSilently(input);
        }
        return null;
    }

    private static String getRemoteChecksum(String group, String artifact, String version)
    {
        return getMavenText(group, artifact, version, null, ".jar.sha1");
    }
    //private static String getMavenText(String group, String artifact, String version)
    //{
    //    return Utils.getMavenText(group, artifact, version, null, null);
    //}
    private static String getMavenText(String group, String artifact, String version, String classifier, String ext)
    {
        String path = group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + (classifier == null ? "" : "-" + classifier) + (ext == null ? ".txt" : ext);
        String ret = downloadString(FORGE_MAVEN + path);
        return ret == null ? null : ret.replaceAll("\r?\n", "");
    }
    private static String downloadMavenFile(File file, String group, String artifact, String version)
    {
        String path = group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + ".jar";
        return downloadFile(FORGE_MAVEN + path, file);
    }

    public static File findMaven(Class<?> jarLocater, boolean isClient)
    {
        File ret = new File("./libraries/"); //Server is working directory/libraries/
        if (isClient)
        {
            try
            {
                File forgeFile = Utils.getJar(jarLocater);
                //Client is harder, we have to un-mavenize the location of Forge.
                //This is nasty as shit, we need a better way..
                File tmp =  Utils.findFirstParent(forgeFile, "libraries");
                if (tmp == null)
                {
                    LogHelper.fatal("Could not determine libraries folder from: " + forgeFile);
                    //return;
                }
                else
                    ret = tmp;
            }
            catch (Exception e1)
            {
                e1.printStackTrace();
                LogHelper.fatal("Could not determine Forge library file");
                return null;
            }
        }
        return ret;
    }

    public static File updateMercurius(File libs, String mcversion)
    {
        String version = Utils.updateMavenVersion(libs, "net.minecraftforge", "Mercurius", mcversion);
        if (version == null)
            return null; //Something went wrong abort!

        File target = Utils.updateMavenFile(libs, "net.minecraftforge", "Mercurius", version);

        if (target == null)
            return null;

        try
        {
            JarFile jar = new JarFile(target);

            byte[] buffer = new byte[BUFFER_SIZE];
            for (JarEntry entry : Collections.list(jar.entries()))
            {
                //Signatures are lazy verified, so we need to make it read all entries in the jar to load up the data
                InputStream input = jar.getInputStream(entry);
                while (input.read(buffer) > 0);
            }

            List<String> invalid = new ArrayList<String>();

            for (JarEntry entry : Collections.list(jar.entries()))
            {
                String name = entry.getName().toUpperCase(Locale.ENGLISH);
                boolean isMetadata = name.endsWith(".SF")
                                  || name.endsWith(".DSA")
                                  || name.endsWith(".RSA");

                name = entry.getName();

                if (isMetadata || entry.isDirectory())
                    continue;

                Certificate[] certs = entry.getCertificates();
                if (certs == null || certs[0] == null)
                    invalid.add(name);
                else
                {
                    try
                    {
                        MessageDigest md = MessageDigest.getInstance("SHA-1");
                        String sig = Utils.bytesToHex(md.digest(certs[0].getEncoded()));
                        if (!sig.equals(FORGEFINGERPRINT))
                            invalid.add(name);
                    }
                    catch (Exception e)
                    {
                        invalid.add(name);
                    }
                }
            }

            closeSilently(jar);

            if (invalid.size() > 0)
            {
                LogHelper.info("Mercurius Jar contains unsigned entries: ");
                Collections.sort(invalid);
                for (String entry : invalid)
                {
                    LogHelper.info("  " + entry);
                }
                return null;
            }
            else
            {
                LogHelper.info("Mercurius Jar contains all signed files! Continueing loading!");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }

        return target;
    }
    private static String updateMavenVersion(File libs, String group, String artifact, String version)
    {
        File target = getMavenFile(libs, group, artifact, version);
        File latestF = new File(target.getAbsoluteFile().getParentFile(), "latest_version.txt");
        String fileVersion = version;

        if (latestF.exists())
        {
            if (latestF.lastModified() < System.currentTimeMillis() - TIMEOUT)
            {
                fileVersion = Utils.readFile(latestF).replaceAll("\r?\n", "");
                LogHelper.info("Version Number exists, but out of date, Updating");
            }
            else
            {
                version = Utils.readFile(latestF).replaceAll("\r?\n", "");
                LogHelper.info("Version Number exists, Using it: " + version);
                return version;
            }
        }

        String remoteVersion = Utils.getMavenText(group, artifact, version, "build_num", ".txt");
        if (fileVersion.equals(remoteVersion))
        {
            LogHelper.info("Remote version up to date: " + fileVersion);
            version = fileVersion;
            if (latestF.exists())
                latestF.setLastModified(System.currentTimeMillis());
        }
        else if (remoteVersion == null)
        {
            LogHelper.error("Could not retreive remote version, check log for details. Assumiing known version is good: " + fileVersion);
            version = fileVersion;
        }
        else
        {
            LogHelper.info("Remote version needs update. Old: " + version + " New: "+ remoteVersion);
            writeFile(latestF, remoteVersion);
            version = remoteVersion;
        }

        return version;
    }
    private static File updateMavenFile(File libs, String group, String artifact, String version)
    {
        File target = getMavenFile(libs, group, artifact, version);
        File shaF = new File(target.getAbsolutePath() + ".sha");

        String checksum = "";
        boolean needsDownload = true;

        if (target.exists())
        {
            LogHelper.info("File exists, Checking hash: " + target.getAbsolutePath());
            String fileChecksum = Utils.getSHA1(target);
            if (shaF.exists())
            {
                checksum = Utils.readFile(shaF).replaceAll("\r?\n", "");
                if (checksum.equals(fileChecksum))
                {
                    if (shaF.lastModified() < System.currentTimeMillis() - TIMEOUT)
                    {
                        LogHelper.info("  Hash matches, However out of date, downloading checksum");
                        String remoteChecksum = Utils.getRemoteChecksum("net.minecraftforge", "Mercurius", version);
                        if (checksum.equals(remoteChecksum))
                        {
                            LogHelper.info("    Remote Checksum verified, not downloading: " + checksum);
                            shaF.setLastModified(System.currentTimeMillis());
                            needsDownload = false;
                        }
                        else
                        {
                            LogHelper.info("    Hash mismatch, Deleting File");
                            LogHelper.info("      Target: " + checksum);
                            LogHelper.info("      Actual: " + remoteChecksum);
                            target.delete();
                        }
                    }
                    else
                    {
                        LogHelper.info("  Hash matches, Skipping download: " + checksum);
                        needsDownload = false;
                    }
                }
                else
                {
                    LogHelper.info("  Hash mismatch, Deleting File");
                    LogHelper.info("    Target: " + checksum);
                    LogHelper.info("    Actual: " + fileChecksum);
                    target.delete();
                }
            }
            else
            {
                LogHelper.info("  Hash file does not exist, but file does. Creating hash file with current hash. with 24 hour timeout.");
                LogHelper.info("    Checksum: " + fileChecksum);
                needsDownload = fileChecksum == null || !writeFile(shaF, fileChecksum); // If we cant write the file, try redownloading
            }
        }

        if (needsDownload)
        {
            if (target.exists())
                target.delete();

            File parent = target.getParentFile();
            if (!parent.exists())
                parent.mkdirs();

            String remoteChecksum = Utils.getRemoteChecksum(group, artifact, version);
            String downloadedChecksum = downloadMavenFile(target, group, artifact, version);
            if (remoteChecksum == null || downloadedChecksum == null)
            {
                LogHelper.info("Downloading failed, exiting!");
                return null;
            }

            if (!remoteChecksum.equals(downloadedChecksum))
            {
                LogHelper.error("Download failed, Invalid checksums!");
                LogHelper.error("  Target: " + remoteChecksum);
                LogHelper.error("  Actual: " + downloadedChecksum);
                target.delete();
                return null;
            }

            LogHelper.info("Download checksums verified: " + remoteChecksum);
            writeFile(shaF, remoteChecksum);
        }

        return target;
    }
}
