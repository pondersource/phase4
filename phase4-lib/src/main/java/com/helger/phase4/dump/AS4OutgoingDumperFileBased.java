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

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.http.HttpHeaderMap;
import com.helger.commons.io.file.FileHelper;
import com.helger.commons.io.file.FilenameHelper;
import com.helger.commons.string.StringHelper;
import com.helger.datetime.util.PDTIOHelper;
import com.helger.phase4.config.AS4Configuration;
import com.helger.phase4.messaging.EAS4MessageMode;
import com.helger.phase4.messaging.IAS4IncomingMessageMetadata;
import com.helger.phase4.mgr.MetaAS4Manager;
import com.helger.phase4.servlet.IAS4MessageState;

/**
 * File based implementation of {@link IAS4OutgoingDumper}. Was moved to this
 * package from <code>com.helger.phase4.servlet.dump</code> in v1.3.0.
 *
 * @author Philip Helger
 * @since 0.9.3
 */
public class AS4OutgoingDumperFileBased extends AbstractAS4OutgoingDumperWithHeaders
{
  @FunctionalInterface
  public interface IFileProvider
  {
    /** The default file extension to be used */
    String DEFAULT_FILE_EXTENSION = ".as4out";

    /**
     * Get the {@link File} to write the dump to. The filename must be globally
     * unique. The resulting file should be an absolute path.
     *
     * @param eMsgMode
     *        Are we dumping a request or a response? Never <code>null</code>.
     *        Added in v1.2.0.
     * @param sAS4MessageID
     *        The AS4 message ID that was send out. Neither <code>null</code>
     *        nor empty.
     * @param nTry
     *        The number of the try to send the message. The initial try has
     *        value 0, the first retry has value 1 etc.
     * @return A non-<code>null</code> {@link File}.
     * @see AS4Configuration#getDumpBasePath()
     */
    @Nonnull
    File getFile (@Nonnull EAS4MessageMode eMsgMode, @Nonnull @Nonempty String sAS4MessageID, @Nonnegative int nTry);

    @Nonnull
    static String getFilename (@Nonnull @Nonempty final String sAS4MessageID, @Nonnegative final int nTry)
    {
      final OffsetDateTime aNow = MetaAS4Manager.getTimestampMgr ().getCurrentDateTime ();
      return aNow.getYear () +
             "/" +
             StringHelper.getLeadingZero (aNow.getMonthValue (), 2) +
             "/" +
             StringHelper.getLeadingZero (aNow.getDayOfMonth (), 2) +
             "/" +
             PDTIOHelper.getTimeForFilename (aNow.toLocalTime ()) +
             "-" +
             FilenameHelper.getAsSecureValidASCIIFilename (sAS4MessageID) +
             "-" +
             nTry +
             DEFAULT_FILE_EXTENSION;
    }
  }

  /**
   * The default relative path for outgoing messages.
   */
  public static final String DEFAULT_BASE_PATH = "outgoing/";
  private static final Logger LOGGER = LoggerFactory.getLogger (AS4OutgoingDumperFileBased.class);

  private final IFileProvider m_aFileProvider;

  /**
   * Default constructor. Writes the files to the AS4 configured data path +
   * {@link #DEFAULT_BASE_PATH}.
   *
   * @see AS4Configuration#getDumpBasePathFile()
   */
  public AS4OutgoingDumperFileBased ()
  {
    this ( (eMsgMode, sMessageID, nTry) -> new File (AS4Configuration.getDumpBasePathFile (),
                                                     DEFAULT_BASE_PATH + IFileProvider.getFilename (sMessageID, nTry)));
  }

  /**
   * Constructor with a custom file provider.
   *
   * @param aFileProvider
   *        The file provider that defines where to store the files. May not be
   *        <code>null</code>.
   */
  public AS4OutgoingDumperFileBased (@Nonnull final IFileProvider aFileProvider)
  {
    ValueEnforcer.notNull (aFileProvider, "FileProvider");
    m_aFileProvider = aFileProvider;
  }

  @Override
  protected OutputStream openOutputStream (@Nonnull final EAS4MessageMode eMsgMode,
                                           @Nullable final IAS4IncomingMessageMetadata aMessageMetadata,
                                           @Nullable final IAS4MessageState aState,
                                           @Nonnull @Nonempty final String sMessageID,
                                           @Nullable final HttpHeaderMap aCustomHeaders,
                                           @Nonnegative final int nTry) throws IOException
  {
    final File aResponseFile = m_aFileProvider.getFile (eMsgMode, sMessageID, nTry);
    if (LOGGER.isInfoEnabled ())
      LOGGER.info ("Logging outgoing AS4 message to '" + aResponseFile.getAbsolutePath () + "'");
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
  public static AS4OutgoingDumperFileBased createForDirectory (@Nonnull final File aBaseDirectory)
  {
    ValueEnforcer.notNull (aBaseDirectory, "BaseDirectory");
    return new AS4OutgoingDumperFileBased ( (eMsgMode, sMessageID, nTry) -> new File (aBaseDirectory,
                                                                                      IFileProvider.getFilename (sMessageID, nTry)));
  }
}
