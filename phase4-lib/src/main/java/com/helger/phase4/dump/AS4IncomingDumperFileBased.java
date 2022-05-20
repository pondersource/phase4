/*
 * Copyright (C) 2015-2022 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.dump;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.http.HttpHeaderMap;
import com.helger.commons.io.file.FileHelper;
import com.helger.commons.io.file.FilenameHelper;
import com.helger.commons.string.StringHelper;
import com.helger.datetime.util.PDTIOHelper;
import com.helger.phase4.config.AS4Configuration;
import com.helger.phase4.messaging.IAS4IncomingMessageMetadata;

/**
 * Simple file based version of {@link IAS4IncomingDumper}. Was moved to this
 * package from <code>com.helger.phase4.servlet.dump</code> in v1.3.0.
 *
 * @author Philip Helger
 * @since 0.9.3
 */
public class AS4IncomingDumperFileBased extends AbstractAS4IncomingDumperWithHeaders
{
  /**
   * Callback interface to create a file based on the provided metadata.
   *
   * @author Philip Helger
   * @since 0.9.8
   */
  @FunctionalInterface
  public interface IFileProvider
  {
    /** The default file extension to be used */
    String DEFAULT_FILE_EXTENSION = ".as4in";

    /**
     * Get the {@link File} to write the dump to. The filename must be globally
     * unique. The resulting file should be an absolute path.
     *
     * @param aMessageMetadata
     *        The message metadata of the incoming message. Never
     *        <code>null</code>.
     * @param aHttpHeaderMap
     *        The HTTP headers of the incoming message. Never <code>null</code>.
     * @return A non-<code>null</code> {@link File}.
     * @see AS4Configuration#getDumpBasePath()
     */
    @Nonnull
    File createFile (@Nonnull IAS4IncomingMessageMetadata aMessageMetadata, @Nonnull HttpHeaderMap aHttpHeaderMap);

    @Nonnull
    static String getFilename (@Nonnull final IAS4IncomingMessageMetadata aMessageMetadata)
    {
      final OffsetDateTime aLDT = aMessageMetadata.getIncomingDT ();
      return aLDT.getYear () +
             "/" +
             StringHelper.getLeadingZero (aLDT.getMonthValue (), 2) +
             "/" +
             StringHelper.getLeadingZero (aLDT.getDayOfMonth (), 2) +
             "/" +
             PDTIOHelper.getTimeForFilename (aLDT.toLocalTime ()) +
             "-" +
             FilenameHelper.getAsSecureValidASCIIFilename (aMessageMetadata.getIncomingUniqueID ()) +
             DEFAULT_FILE_EXTENSION;
    }
  }

  /**
   * The default relative path for incoming messages.
   */
  public static final String DEFAULT_BASE_PATH = "incoming/";
  private static final Logger LOGGER = LoggerFactory.getLogger (AS4IncomingDumperFileBased.class);

  private final IFileProvider m_aFileProvider;

  /**
   * Default constructor. Writes the files to the AS4 configured data path +
   * {@link #DEFAULT_BASE_PATH}.
   *
   * @see AS4Configuration#getDumpBasePathFile()
   */
  public AS4IncomingDumperFileBased ()
  {
    this ( (aMessageMetadata, aHttpHeaderMap) -> new File (AS4Configuration.getDumpBasePathFile (),
                                                           DEFAULT_BASE_PATH + IFileProvider.getFilename (aMessageMetadata)));
  }

  /**
   * Constructor with a custom file provider.
   *
   * @param aFileProvider
   *        The file provider that defines where to store the files. May not be
   *        <code>null</code>.
   */
  public AS4IncomingDumperFileBased (@Nonnull final IFileProvider aFileProvider)
  {
    ValueEnforcer.notNull (aFileProvider, "FileProvider");
    m_aFileProvider = aFileProvider;
  }

  @Override
  @Nullable
  protected OutputStream openOutputStream (@Nonnull final IAS4IncomingMessageMetadata aMessageMetadata,
                                           @Nonnull final HttpHeaderMap aHttpHeaderMap) throws IOException
  {
    final File aResponseFile = m_aFileProvider.createFile (aMessageMetadata, aHttpHeaderMap);
    if (LOGGER.isInfoEnabled ())
      LOGGER.info ("Logging incoming AS4 message to '" + aResponseFile.getAbsolutePath () + "'");
    return FileHelper.getBufferedOutputStream (aResponseFile);
  }

  /**
   * Create a new instance for the provided directory.
   *
   * @param aBaseDirectory
   *        The absolute directory to be used. May not be <code>null</code>.
   * @return The created dumper. Never <code>null</code>.
   * @since 0.10.2
   */
  @Nonnull
  public static AS4IncomingDumperFileBased createForDirectory (@Nonnull final File aBaseDirectory)
  {
    ValueEnforcer.notNull (aBaseDirectory, "BaseDirectory");
    return new AS4IncomingDumperFileBased ( (aMessageMetadata, aHttpHeaderMap) -> new File (aBaseDirectory,
                                                                                            IFileProvider.getFilename (aMessageMetadata)));
  }
}
