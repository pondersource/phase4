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
package com.helger.phase4.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.WillClose;
import javax.annotation.WillNotClose;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.xml.namespace.QName;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.helger.commons.error.IError;
import com.helger.commons.error.list.ErrorList;
import com.helger.commons.http.CHttpHeader;
import com.helger.commons.http.HttpHeaderMap;
import com.helger.commons.io.IHasInputStream;
import com.helger.commons.io.stream.HasInputStream;
import com.helger.commons.io.stream.NonBlockingByteArrayInputStream;
import com.helger.commons.mime.IMimeType;
import com.helger.commons.mime.MimeTypeParser;
import com.helger.commons.string.StringHelper;
import com.helger.commons.wrapper.Wrapper;
import com.helger.phase4.attachment.AS4DecompressException;
import com.helger.phase4.attachment.EAS4CompressionMode;
import com.helger.phase4.attachment.IAS4IncomingAttachmentFactory;
import com.helger.phase4.attachment.WSS4JAttachment;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.phase4.dump.AS4DumpManager;
import com.helger.phase4.dump.IAS4IncomingDumper;
import com.helger.phase4.ebms3header.Ebms3Error;
import com.helger.phase4.ebms3header.Ebms3PartInfo;
import com.helger.phase4.ebms3header.Ebms3Property;
import com.helger.phase4.ebms3header.Ebms3PullRequest;
import com.helger.phase4.ebms3header.Ebms3Receipt;
import com.helger.phase4.ebms3header.Ebms3SignalMessage;
import com.helger.phase4.ebms3header.Ebms3UserMessage;
import com.helger.phase4.error.EEbmsError;
import com.helger.phase4.messaging.IAS4IncomingMessageMetadata;
import com.helger.phase4.messaging.domain.MessageHelperMethods;
import com.helger.phase4.mgr.MetaAS4Manager;
import com.helger.phase4.model.AS4Helper;
import com.helger.phase4.model.pmode.IPMode;
import com.helger.phase4.model.pmode.leg.PModeLeg;
import com.helger.phase4.model.pmode.resolve.IPModeResolver;
import com.helger.phase4.profile.IAS4Profile;
import com.helger.phase4.profile.IAS4ProfileValidator;
import com.helger.phase4.servlet.soap.AS4SingleSOAPHeader;
import com.helger.phase4.servlet.soap.ISOAPHeaderElementProcessor;
import com.helger.phase4.servlet.soap.SOAPHeaderElementProcessorRegistry;
import com.helger.phase4.soap.ESoapVersion;
import com.helger.phase4.util.AS4ResourceHelper;
import com.helger.phase4.util.AS4XMLHelper;
import com.helger.phase4.util.Phase4Exception;
import com.helger.web.multipart.MultipartProgressNotifier;
import com.helger.web.multipart.MultipartStream;
import com.helger.web.multipart.MultipartStream.MultipartItemInputStream;
import com.helger.xml.ChildElementIterator;
import com.helger.xml.XMLHelper;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Utility methods for incoming AS4 messages.
 *
 * @author Philip Helger
 * @since v0.9.7
 */
public class AS4IncomingHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS4IncomingHandler.class);

  private AS4IncomingHandler ()
  {}

  /**
   * Callback interface for handling the parsing result.
   *
   * @author Philip Helger
   */
  public interface IAS4ParsedMessageCallback
  {
    /**
     * Callback method
     *
     * @param aHttpHeaders
     *        Incoming HTTP headers. Never <code>null</code> but maybe empty.
     * @param aSoapDocument
     *        Parsed SOAP document. Never <code>null</code>.
     * @param eSoapVersion
     *        SOAP version in use. Never <code>null</code>.
     * @param aIncomingAttachments
     *        Incoming attachments. Never <code>null</code> but maybe empty.
     * @throws WSSecurityException
     *         In case of WSS4J errors
     * @throws MessagingException
     *         In case of MIME errors
     * @throws Phase4Exception
     *         In case of a processing error (since 0.9.11)
     */
    void handle (@Nonnull HttpHeaderMap aHttpHeaders,
                 @Nonnull Document aSoapDocument,
                 @Nonnull ESoapVersion eSoapVersion,
                 @Nonnull ICommonsList <WSS4JAttachment> aIncomingAttachments) throws WSSecurityException,
                                                                               MessagingException,
                                                                               Phase4Exception;
  }

  public static void parseAS4Message (@Nonnull final IAS4IncomingAttachmentFactory aIAF,
                                      @Nonnull @WillNotClose final AS4ResourceHelper aResHelper,
                                      @Nonnull final IAS4IncomingMessageMetadata aMessageMetadata,
                                      @Nonnull @WillClose final InputStream aPayloadIS,
                                      @Nonnull final HttpHeaderMap aHttpHeaders,
                                      @Nonnull final IAS4ParsedMessageCallback aCallback,
                                      @Nullable final IAS4IncomingDumper aIncomingDumper) throws Phase4Exception,
                                                                                          IOException,
                                                                                          MessagingException,
                                                                                          WSSecurityException
  {
    // Determine content type
    final String sContentType = aHttpHeaders.getFirstHeaderValue (CHttpHeader.CONTENT_TYPE);
    if (StringHelper.hasNoText (sContentType))
      throw new Phase4Exception ("Content-Type header is missing");

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Received Content-Type string: '" + sContentType + "'");
    final IMimeType aContentType = MimeTypeParser.safeParseMimeType (sContentType);
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Received Content-Type object: " + aContentType);
    if (aContentType == null)
      throw new Phase4Exception ("Failed to parse Content-Type '" + sContentType + "'");
    final IMimeType aPlainContentType = aContentType.getCopyWithoutParameters ();

    // Fallback to global dumper if none is provided
    final IAS4IncomingDumper aRealIncomingDumper = aIncomingDumper != null ? aIncomingDumper : AS4DumpManager.getIncomingDumper ();

    Document aSoapDocument = null;
    ESoapVersion eSoapVersion = null;
    final ICommonsList <WSS4JAttachment> aIncomingAttachments = new CommonsArrayList <> ();
    final Wrapper <OutputStream> aDumpOSHolder = new Wrapper <> ();

    if (aPlainContentType.equals (AS4RequestHandler.MT_MULTIPART_RELATED))
    {
      // MIME message
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Received MIME message");

      final String sBoundary = aContentType.getParameterValueWithName ("boundary");
      if (StringHelper.hasNoText (sBoundary))
        throw new Phase4Exception ("Content-Type '" + sContentType + "' misses 'boundary' parameter");

      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("MIME Boundary: '" + sBoundary + "'");

      // Ensure the stream gets closed correctly
      try (final InputStream aRequestIS = AS4DumpManager.getIncomingDumpAwareInputStream (aRealIncomingDumper,
                                                                                          aPayloadIS,
                                                                                          aMessageMetadata,
                                                                                          aHttpHeaders,
                                                                                          aDumpOSHolder))
      {
        // PARSING MIME Message via MultipartStream
        final MultipartStream aMulti = new MultipartStream (aRequestIS,
                                                            sBoundary.getBytes (StandardCharsets.ISO_8859_1),
                                                            (MultipartProgressNotifier) null);

        int nIndex = 0;
        while (true)
        {
          final boolean bHasNextPart = nIndex == 0 ? aMulti.skipPreamble () : aMulti.readBoundary ();
          if (!bHasNextPart)
            break;

          if (LOGGER.isDebugEnabled ())
            LOGGER.debug ("Found MIME part #" + nIndex);

          try (final MultipartItemInputStream aBodyPartIS = aMulti.createInputStream ())
          {
            // Read headers AND content
            final MimeBodyPart aBodyPart = new MimeBodyPart (aBodyPartIS);

            if (nIndex == 0)
            {
              // First MIME part -> SOAP document
              if (LOGGER.isDebugEnabled ())
                LOGGER.debug ("Parsing first MIME part as SOAP document");

              // Read SOAP document
              aSoapDocument = DOMReader.readXMLDOM (aBodyPart.getInputStream ());

              IMimeType aPlainPartMT = MimeTypeParser.safeParseMimeType (aBodyPart.getContentType ());
              if (aPlainPartMT != null)
                aPlainPartMT = aPlainPartMT.getCopyWithoutParameters ();

              // Determine SOAP version from MIME part content type
              eSoapVersion = ESoapVersion.getFromMimeTypeOrNull (aPlainPartMT);
              if (eSoapVersion != null && LOGGER.isDebugEnabled ())
                LOGGER.debug ("Determined SOAP version " + eSoapVersion + " from Content-Type");

              if (eSoapVersion == null && aSoapDocument != null)
              {
                // Determine SOAP version from the read document
                eSoapVersion = ESoapVersion.getFromNamespaceURIOrNull (XMLHelper.getNamespaceURI (aSoapDocument));
                if (eSoapVersion != null && LOGGER.isDebugEnabled ())
                  LOGGER.debug ("Determined SOAP version " + eSoapVersion + " from XML root element namespace URI");
              }
            }
            else
            {
              // MIME Attachment (index is gt 0)
              if (LOGGER.isDebugEnabled ())
                LOGGER.debug ("Parsing MIME part #" + nIndex + " as attachment");

              final WSS4JAttachment aAttachment = aIAF.createAttachment (aBodyPart, aResHelper);
              aIncomingAttachments.add (aAttachment);
            }
          }
          nIndex++;
        }
      }
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Read MIME message with " + aIncomingAttachments.size () + " attachment(s)");
    }
    else
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Received plain message");

      // Expect plain SOAP - read whole request to DOM
      // Note: this may require a huge amount of memory for large requests
      aSoapDocument = DOMReader.readXMLDOM (AS4DumpManager.getIncomingDumpAwareInputStream (aRealIncomingDumper,
                                                                                            aPayloadIS,
                                                                                            aMessageMetadata,
                                                                                            aHttpHeaders,
                                                                                            aDumpOSHolder));

      if (LOGGER.isDebugEnabled ())
        if (aSoapDocument != null)
          LOGGER.debug ("Successfully parsed payload as XML");
        else
          LOGGER.debug ("Failed to parse payload as XML");

      if (aSoapDocument != null)
      {
        // Determine SOAP version from the read document
        eSoapVersion = ESoapVersion.getFromNamespaceURIOrNull (XMLHelper.getNamespaceURI (aSoapDocument));
        if (eSoapVersion != null && LOGGER.isDebugEnabled ())
          LOGGER.debug ("Determined SOAP version " + eSoapVersion + " from XML root element namespace URI");
      }

      if (eSoapVersion == null)
      {
        // Determine SOAP version from content type
        eSoapVersion = ESoapVersion.getFromMimeTypeOrNull (aPlainContentType);
        if (eSoapVersion != null && LOGGER.isDebugEnabled ())
          LOGGER.debug ("Determined SOAP version " + eSoapVersion + " from Content-Type");
      }
    }

    try
    {
      if (aSoapDocument == null)
      {
        // We don't have a SOAP document
        throw new Phase4Exception (eSoapVersion == null ? "Failed to parse incoming message!"
                                                        : "Failed to parse incoming SOAP " + eSoapVersion.getVersion () + " document!");
      }

      if (eSoapVersion == null)
      {
        // We're missing a SOAP version
        throw new Phase4Exception ("Failed to determine SOAP version of XML document!");
      }

      aCallback.handle (aHttpHeaders, aSoapDocument, eSoapVersion, aIncomingAttachments);
    }
    finally
    {
      // Here, the incoming dump is finally ready closed and usable
      if (aRealIncomingDumper != null && aDumpOSHolder.isSet ())
        try
        {
          aRealIncomingDumper.onEndRequest (aMessageMetadata);
        }
        catch (final Exception ex)
        {
          LOGGER.error ("IncomingDumper.onEndRequest failed. Dumper=" + aRealIncomingDumper + "; MessageMetadata=" + aMessageMetadata, ex);
        }
    }
  }

  private static void _processSoapHeaderElements (@Nonnull final SOAPHeaderElementProcessorRegistry aRegistry,
                                                  @Nonnull final Document aSoapDocument,
                                                  @Nonnull final ICommonsList <WSS4JAttachment> aIncomingAttachments,
                                                  @Nonnull final AS4MessageState aState,
                                                  @Nonnull final ICommonsList <Ebms3Error> aErrorMessages) throws Phase4Exception
  {
    final ESoapVersion eSoapVersion = aState.getSoapVersion ();
    final ICommonsList <AS4SingleSOAPHeader> aHeaders = new CommonsArrayList <> ();
    {
      // Find SOAP header
      final Node aHeaderNode = XMLHelper.getFirstChildElementOfName (aSoapDocument.getDocumentElement (),
                                                                     eSoapVersion.getNamespaceURI (),
                                                                     eSoapVersion.getHeaderElementName ());
      if (aHeaderNode == null)
        throw new Phase4Exception ("SOAP document is missing a Header element {" +
                                   eSoapVersion.getNamespaceURI () +
                                   "}" +
                                   eSoapVersion.getHeaderElementName ());

      // Extract all header elements including their "mustUnderstand" value
      for (final Element aHeaderChild : new ChildElementIterator (aHeaderNode))
      {
        final QName aQName = XMLHelper.getQName (aHeaderChild);
        final String sMustUnderstand = aHeaderChild.getAttributeNS (eSoapVersion.getNamespaceURI (), "mustUnderstand");
        final boolean bIsMustUnderstand = eSoapVersion.getMustUnderstandValue (true).equals (sMustUnderstand);
        aHeaders.add (new AS4SingleSOAPHeader (aHeaderChild, aQName, bIsMustUnderstand));
      }
    }

    final ICommonsOrderedMap <QName, ISOAPHeaderElementProcessor> aAllProcessors = aRegistry.getAllElementProcessors ();
    if (aAllProcessors.isEmpty ())
      LOGGER.error ("No SOAP Header element processor is registered");

    // handle all headers in the order of the registered handlers!
    for (final Map.Entry <QName, ISOAPHeaderElementProcessor> aEntry : aAllProcessors.entrySet ())
    {
      final QName aQName = aEntry.getKey ();

      // Check if this message contains a header for the current handler
      final AS4SingleSOAPHeader aHeader = aHeaders.findFirst (x -> aQName.equals (x.getQName ()));
      if (aHeader == null)
      {
        // no header element for current processor
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Message contains no SOAP header element with QName " + aQName.toString ());
        continue;
      }

      final ISOAPHeaderElementProcessor aProcessor = aEntry.getValue ();
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Processing SOAP header element " + aQName.toString () + " with processor " + aProcessor);

      // Error list for this processor
      final ErrorList aErrorList = new ErrorList ();

      try
      {
        // Process element
        if (aProcessor.processHeaderElement (aSoapDocument, aHeader.getNode (), aIncomingAttachments, aState, aErrorList).isSuccess ())
        {
          // Mark header as processed (for mustUnderstand check)
          aHeader.setProcessed (true);
        }
        else
        {
          // upon failure, the element stays unprocessed and sends back a signal
          // message with the errors
          LOGGER.error ("Failed to process SOAP header element " +
                        aQName.toString () +
                        " with processor " +
                        aProcessor +
                        "; error details: " +
                        aErrorList);

          final String sRefToMessageID = aState.getMessageID ();
          final Locale aLocale = aState.getLocale ();
          for (final IError aError : aErrorList)
          {
            final EEbmsError ePredefinedError = EEbmsError.getFromErrorCodeOrNull (aError.getErrorID ());
            if (ePredefinedError != null)
              aErrorMessages.add (ePredefinedError.getAsEbms3Error (aLocale, sRefToMessageID));
            else
            {
              final Ebms3Error aEbms3Error = new Ebms3Error ();
              aEbms3Error.setErrorDetail (aError.getErrorText (aLocale));
              aEbms3Error.setErrorCode (aError.getErrorID ());
              aEbms3Error.setSeverity (aError.getErrorLevel ().getID ());
              aEbms3Error.setOrigin (aError.getErrorFieldName ());
              aEbms3Error.setRefToMessageInError (sRefToMessageID);
              aErrorMessages.add (aEbms3Error);
            }
          }

          // Stop processing of other headers
          break;
        }
      }
      catch (final Exception ex)
      {
        // upon failure, the element stays unprocessed and sends back a signal
        // message with the errors
        LOGGER.error ("Error processing SOAP header element " + aQName.toString () + " with processor " + aProcessor, ex);
        aErrorMessages.add (EEbmsError.EBMS_OTHER.getAsEbms3Error (aState.getLocale (),
                                                                   aState.getMessageID (),
                                                                   "Error processing SOAP header element " + aQName.toString ()));

        // Stop processing of other headers
        break;
      }
    }

    // If an error message is present, send it back gracefully
    if (aErrorMessages.isEmpty ())
    {
      // Now check if all must understand headers were processed
      // Are all must-understand headers processed?
      for (final AS4SingleSOAPHeader aHeader : aHeaders)
        if (aHeader.isMustUnderstand () && !aHeader.isProcessed ())
          throw new Phase4Exception ("Required SOAP header element " + aHeader.getQName ().toString () + " could not be handled");
    }
  }

  private static void _decompressAttachments (@Nonnull final ICommonsList <WSS4JAttachment> aIncomingDecryptedAttachments,
                                              @Nonnull final Ebms3UserMessage aUserMessage,
                                              @Nonnull final IAS4MessageState aState)
  {
    // For all incoming attachments
    for (final WSS4JAttachment aIncomingAttachment : aIncomingDecryptedAttachments.getClone ())
    {
      final EAS4CompressionMode eCompressionMode = aState.getAttachmentCompressionMode (aIncomingAttachment.getId ());
      if (eCompressionMode != null)
      {
        final IHasInputStream aOldISP = aIncomingAttachment.getInputStreamProvider ();
        aIncomingAttachment.setSourceStreamProvider (new HasInputStream ( () -> {
          try
          {
            final InputStream aSrcIS = aOldISP.getInputStream ();
            if (aSrcIS == null)
              throw new IllegalStateException ("Failed to create InputStream from " + aOldISP);
            if (LOGGER.isDebugEnabled ())
              LOGGER.debug ("Decompressing attachment with ID '" + aIncomingAttachment.getId () + "' using " + eCompressionMode);
            return eCompressionMode.getDecompressStream (aSrcIS);
          }
          catch (final IOException ex)
          {
            // This is e.g. invoked, if the GZIP decompression failed because of
            // invalid payload
            throw new AS4DecompressException (ex);
          }
        }, aOldISP.isReadMultiple ()));

        // Remember the compression mode
        aIncomingAttachment.setCompressionMode (eCompressionMode);

        final String sAttachmentContentID = StringHelper.trimStart (aIncomingAttachment.getId (), "attachment=");
        // x.getHref() != null needed since, if a message contains a payload and
        // an attachment, it would throw a NullPointerException since a payload
        // does not have anything written in its partinfo therefore also now
        // href
        final Ebms3PartInfo aPartInfo = CollectionHelper.findFirst (aUserMessage.getPayloadInfo ().getPartInfo (),
                                                                    x -> x.getHref () != null &&
                                                                         x.getHref ().contains (sAttachmentContentID));
        if (aPartInfo != null && aPartInfo.getPartProperties () != null)
        {
          // Find MimeType property
          final Ebms3Property aProperty = CollectionHelper.findFirst (aPartInfo.getPartProperties ().getProperty (),
                                                                      x -> x.getName ()
                                                                            .equalsIgnoreCase (MessageHelperMethods.PART_PROPERTY_MIME_TYPE));
          if (aProperty != null)
          {
            final String sMimeType = aProperty.getValue ();
            if (MimeTypeParser.safeParseMimeType (sMimeType) == null)
              LOGGER.warn ("Value '" +
                           sMimeType +
                           "' of property '" +
                           MessageHelperMethods.PART_PROPERTY_MIME_TYPE +
                           "' is not a valid MIME type");
            aIncomingAttachment.overwriteMimeType (sMimeType);
          }
        }
      }
    }
  }

  @Nonnull
  public static IAS4MessageState processEbmsMessage (@Nonnull @WillNotClose final AS4ResourceHelper aResHelper,
                                                     @Nonnull final Locale aLocale,
                                                     @Nonnull final SOAPHeaderElementProcessorRegistry aRegistry,
                                                     @Nonnull final HttpHeaderMap aHttpHeaders,
                                                     @Nonnull final Document aSoapDocument,
                                                     @Nonnull final ESoapVersion eSoapVersion,
                                                     @Nonnull final ICommonsList <WSS4JAttachment> aIncomingAttachments,
                                                     @Nonnull final IAS4IncomingProfileSelector aAS4ProfileSelector,
                                                     @Nonnull final ICommonsList <Ebms3Error> aErrorMessagesTarget) throws Phase4Exception
  {
    ValueEnforcer.notNull (aResHelper, "ResHelper");
    ValueEnforcer.notNull (aLocale, "Locale");
    ValueEnforcer.notNull (aHttpHeaders, "HttpHeaders");
    ValueEnforcer.notNull (aSoapDocument, "SoapDocument");
    ValueEnforcer.notNull (eSoapVersion, "SoapVersion");
    ValueEnforcer.notNull (aIncomingAttachments, "IncomingAttachments");
    ValueEnforcer.notNull (aAS4ProfileSelector, "AS4ProfileSelector");
    ValueEnforcer.notNull (aErrorMessagesTarget, "aErrorMessagesTarget");

    if (LOGGER.isDebugEnabled ())
    {
      LOGGER.debug ("Received the following SOAP " + eSoapVersion.getVersion () + " document:");
      LOGGER.debug (AS4XMLHelper.serializeXML (aSoapDocument));
      if (aIncomingAttachments.isEmpty ())
      {
        LOGGER.debug ("Without any incoming attachments");
      }
      else
      {
        LOGGER.debug ("Including the following " + aIncomingAttachments.size () + " attachments:");
        LOGGER.debug (aIncomingAttachments.toString ());
      }
    }

    // This is where all data from the SOAP headers is stored to
    final AS4MessageState aState = new AS4MessageState (eSoapVersion, aResHelper, aLocale);

    // Handle all headers - modifies the state
    _processSoapHeaderElements (aRegistry, aSoapDocument, aIncomingAttachments, aState, aErrorMessagesTarget);

    // Remember if header processing was successful or not
    final boolean bSoapHeaderElementProcessingSuccess = aErrorMessagesTarget.isEmpty ();
    aState.setSoapHeaderElementProcessingSuccessful (bSoapHeaderElementProcessingSuccess);
    if (bSoapHeaderElementProcessingSuccess)
    {
      // Every message can only contain 1 User message or 1 pull message
      // aUserMessage can be null on incoming Pull-Message!
      final Ebms3UserMessage aEbmsUserMessage = aState.getEbmsUserMessage ();
      final Ebms3Error aEbmsError = aState.getEbmsError ();
      final Ebms3PullRequest aEbmsPullRequest = aState.getEbmsPullRequest ();
      final Ebms3Receipt aEbmsReceipt = aState.getEbmsReceipt ();

      // Check payload consistency
      final int nCountData = (aEbmsUserMessage != null ? 1 : 0) +
                             (aEbmsPullRequest != null ? 1 : 0) +
                             (aEbmsReceipt != null ? 1 : 0) +
                             (aEbmsError != null ? 1 : 0);
      if (nCountData != 1)
      {
        LOGGER.error ("Expected a UserMessage(" +
                      (aEbmsUserMessage != null ? 1 : 0) +
                      "), a PullRequest(" +
                      (aEbmsPullRequest != null ? 1 : 0) +
                      "), a Receipt(" +
                      (aEbmsReceipt != null ? 1 : 0) +
                      ") or an Error(" +
                      (aEbmsError != null ? 1 : 0) +
                      ")");

        // send EBMS:0001 error back
        aErrorMessagesTarget.add (EEbmsError.EBMS_VALUE_NOT_RECOGNIZED.getAsEbms3Error (aLocale, aState.getMessageID ()));
      }

      // Determine AS4 profile ID (since 0.13.0)
      final String sProfileID = aAS4ProfileSelector.getAS4ProfileID (aState);
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Determined AS4 profile ID '" + sProfileID + "' for current message");
      aState.setProfileID (sProfileID);

      final IPMode aPMode = aState.getPMode ();
      final PModeLeg aEffectiveLeg = aState.getEffectivePModeLeg ();

      if (aEbmsUserMessage != null)
      {
        // User message requires PMode
        if (aPMode == null)
          throw new Phase4Exception ("No AS4 P-Mode configuration found for user-message!");

        // Only check leg if the message is a usermessage
        if (aEffectiveLeg == null)
          throw new Phase4Exception ("No AS4 P-Mode leg could be determined!");

        // Only do profile checks if a profile is set
        if (StringHelper.hasText (sProfileID))
        {
          // Resolve profile ID
          final IAS4Profile aProfile = MetaAS4Manager.getProfileMgr ().getProfileOfID (sProfileID);
          if (aProfile == null)
            throw new IllegalStateException ("The configured AS4 profile '" + sProfileID + "' does not exist.");

          // Profile Checks gets set when started with Server
          final IAS4ProfileValidator aValidator = aProfile.getValidator ();
          if (aValidator != null)
          {
            if (aAS4ProfileSelector.validateAgainstProfile ())
            {
              final ErrorList aErrorList = new ErrorList ();
              aValidator.validatePMode (aPMode, aErrorList);
              aValidator.validateUserMessage (aEbmsUserMessage, aErrorList);
              if (aErrorList.isNotEmpty ())
              {
                throw new Phase4Exception ("Error validating incoming AS4 message with the profile " +
                                           aProfile.getDisplayName () +
                                           "\n Following errors are present: " +
                                           aErrorList.getAllErrors ().getAllTexts (aLocale));
              }
            }
            else
            {
              LOGGER.warn ("The AS4 profile '" +
                           sProfileID +
                           "' has a validation configured, but the usage was disabled using the AS4ProfileSelector");
            }
          }
        }
        else
        {
          if (LOGGER.isDebugEnabled ())
            LOGGER.debug ("AS4 state contains no AS4 profile ID - therefore no consistency checks are performed");
        }

        // Ensure the decrypted attachments are used
        final ICommonsList <WSS4JAttachment> aDecryptedAttachments = aState.hasDecryptedAttachments () ? aState.getDecryptedAttachments ()
                                                                                                       : aState.getOriginalAttachments ();

        // Decompress attachments (if compressed)
        // Result is directly in the decrypted attachments list!
        _decompressAttachments (aDecryptedAttachments, aEbmsUserMessage, aState);
      }
      else
      {
        // Signal message

        // Pull-request also requires PMode
        if (aEbmsPullRequest != null && aPMode == null)
          throw new Phase4Exception ("No AS4 P-Mode configuration found for pull-request!");
      }

      final boolean bUseDecryptedSOAP = aState.hasDecryptedSoapDocument ();
      final Document aRealSOAPDoc = bUseDecryptedSOAP ? aState.getDecryptedSoapDocument () : aSoapDocument;
      assert aRealSOAPDoc != null;

      // Find SOAP body (mandatory according to SOAP XSD)
      final Node aBodyNode = XMLHelper.getFirstChildElementOfName (aRealSOAPDoc.getDocumentElement (),
                                                                   eSoapVersion.getNamespaceURI (),
                                                                   eSoapVersion.getBodyElementName ());
      if (aBodyNode == null)
        throw new Phase4Exception ((bUseDecryptedSOAP ? "Decrypted" : "Original") + " SOAP document is missing a Body element");
      aState.setSoapBodyPayloadNode (aBodyNode.getFirstChild ());

      final boolean bIsPingMessage = AS4Helper.isPingMessage (aPMode);
      aState.setPingMessage (bIsPingMessage);
      if (bIsPingMessage)
        LOGGER.info ("Received an AS4 Ping message - meaning it will NOT be handled by the custom handlers.");
    }

    return aState;
  }

  @Nullable
  private static IAS4MessageState _parseMessage (@Nonnull final IAS4CryptoFactory aCryptoFactory,
                                                 @Nonnull final IPModeResolver aPModeResolver,
                                                 @Nonnull final IAS4IncomingAttachmentFactory aIAF,
                                                 @Nonnull final IAS4IncomingProfileSelector aAS4ProfileSelector,
                                                 @Nonnull @WillNotClose final AS4ResourceHelper aResHelper,
                                                 @Nullable final IPMode aSendingPMode,
                                                 @Nonnull final Locale aLocale,
                                                 @Nonnull final IAS4IncomingMessageMetadata aMessageMetadata,
                                                 @Nonnull final HttpResponse aHttpResponse,
                                                 @Nonnull final byte [] aResponsePayload,
                                                 @Nullable final IAS4IncomingDumper aIncomingDumper) throws Phase4Exception
  {
    // This wrapper will take the result
    final Wrapper <IAS4MessageState> aRetWrapper = new Wrapper <> ();

    // Handler for the parsed message
    final IAS4ParsedMessageCallback aCallback = (aHttpHeaders, aSoapDocument, eSoapVersion, aIncomingAttachments) -> {
      final ICommonsList <Ebms3Error> aErrorMessages = new CommonsArrayList <> ();

      // Use the sending PMode as fallback, because from the incoming
      // receipt/error it is impossible to detect a PMode
      final SOAPHeaderElementProcessorRegistry aRegistry = SOAPHeaderElementProcessorRegistry.createDefault (aPModeResolver,
                                                                                                             aCryptoFactory,
                                                                                                             aSendingPMode);

      // Parse AS4, verify signature etc
      final IAS4MessageState aState = processEbmsMessage (aResHelper,
                                                          aLocale,
                                                          aRegistry,
                                                          aHttpHeaders,
                                                          aSoapDocument,
                                                          eSoapVersion,
                                                          aIncomingAttachments,
                                                          aAS4ProfileSelector,
                                                          aErrorMessages);

      if (aState.isSoapHeaderElementProcessingSuccessful ())
      {
        // Remember the parsed signal message
        aRetWrapper.set (aState);
      }
      else
      {
        throw new Phase4Exception ("Error processing AS4 message", aState.getSoapWSS4JException ());
      }
    };

    // Create header map from response headers
    final HttpHeaderMap aHttpHeaders = new HttpHeaderMap ();
    for (final Header aHeader : aHttpResponse.getAllHeaders ())
      aHttpHeaders.addHeader (aHeader.getName (), aHeader.getValue ());

    try (final NonBlockingByteArrayInputStream aPayloadIS = new NonBlockingByteArrayInputStream (aResponsePayload))
    {
      // Parse incoming message
      parseAS4Message (aIAF, aResHelper, aMessageMetadata, aPayloadIS, aHttpHeaders, aCallback, aIncomingDumper);
    }
    catch (final Phase4Exception ex)
    {
      throw ex;
    }
    catch (final Exception ex)
    {
      throw new Phase4Exception ("Error parsing AS4 message", ex);
    }

    // This one contains the result
    return aRetWrapper.get ();
  }

  @Nullable
  public static Ebms3SignalMessage parseSignalMessage (@Nonnull final IAS4CryptoFactory aCryptoFactory,
                                                       @Nonnull final IPModeResolver aPModeResolver,
                                                       @Nonnull final IAS4IncomingAttachmentFactory aIAF,
                                                       @Nonnull final IAS4IncomingProfileSelector aAS4ProfileSelector,
                                                       @Nonnull @WillNotClose final AS4ResourceHelper aResHelper,
                                                       @Nullable final IPMode aSendingPMode,
                                                       @Nonnull final Locale aLocale,
                                                       @Nonnull final IAS4IncomingMessageMetadata aMessageMetadata,
                                                       @Nonnull final HttpResponse aHttpResponse,
                                                       @Nonnull final byte [] aResponsePayload,
                                                       @Nullable final IAS4IncomingDumper aIncomingDumper) throws Phase4Exception
  {
    final IAS4MessageState aState = _parseMessage (aCryptoFactory,
                                                   aPModeResolver,
                                                   aIAF,
                                                   aAS4ProfileSelector,
                                                   aResHelper,
                                                   aSendingPMode,
                                                   aLocale,
                                                   aMessageMetadata,
                                                   aHttpResponse,
                                                   aResponsePayload,
                                                   aIncomingDumper);
    if (aState == null)
    {
      // Error message was already logged
      return null;
    }

    final Ebms3SignalMessage ret = aState.getEbmsSignalMessage ();
    if (ret == null)
    {
      if (aState.getEbmsUserMessage () != null)
        LOGGER.warn ("A Message state is present, but it contains a UserMessage instead of a SignalMessage.");
      else
        LOGGER.warn ("A Message state is present, but it contains neither a UserMessage nor a SignalMessage.");
    }
    return ret;
  }

  @Nullable
  public static Ebms3UserMessage parseUserMessage (@Nonnull final IAS4CryptoFactory aCryptoFactory,
                                                   @Nonnull final IPModeResolver aPModeResolver,
                                                   @Nonnull final IAS4IncomingAttachmentFactory aIAF,
                                                   @Nonnull final IAS4IncomingProfileSelector aAS4ProfileSelector,
                                                   @Nonnull @WillNotClose final AS4ResourceHelper aResHelper,
                                                   @Nullable final IPMode aSendingPMode,
                                                   @Nonnull final Locale aLocale,
                                                   @Nonnull final IAS4IncomingMessageMetadata aMessageMetadata,
                                                   @Nonnull final HttpResponse aHttpResponse,
                                                   @Nonnull final byte [] aResponsePayload,
                                                   @Nullable final IAS4IncomingDumper aIncomingDumper) throws Phase4Exception
  {
    final IAS4MessageState aState = _parseMessage (aCryptoFactory,
                                                   aPModeResolver,
                                                   aIAF,
                                                   aAS4ProfileSelector,
                                                   aResHelper,
                                                   aSendingPMode,
                                                   aLocale,
                                                   aMessageMetadata,
                                                   aHttpResponse,
                                                   aResponsePayload,
                                                   aIncomingDumper);
    if (aState == null)
    {
      // Error message was already logged
      return null;
    }

    final Ebms3UserMessage ret = aState.getEbmsUserMessage ();
    if (ret == null)
    {
      if (aState.getEbmsSignalMessage () != null)
        LOGGER.warn ("A Message state is present, but it contains a SignalMessage instead of a UserMessage.");
      else
        LOGGER.warn ("A Message state is present, but it contains neither a SignalMessage nor a UserMessage.");
    }
    return ret;
  }
}
