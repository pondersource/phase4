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
package com.helger.phase4.peppol;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unece.cefact.namespaces.sbdh.StandardBusinessDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.datetime.XMLOffsetDateTime;
import com.helger.commons.mime.CMimeType;
import com.helger.commons.mime.IMimeType;
import com.helger.commons.state.ESuccess;
import com.helger.commons.state.ETriState;
import com.helger.commons.string.StringHelper;
import com.helger.peppol.sbdh.PeppolSBDHDocument;
import com.helger.peppol.sbdh.payload.PeppolSBDHPayloadWriter;
import com.helger.peppol.sbdh.spec12.BinaryContentType;
import com.helger.peppol.sbdh.spec12.TextContentType;
import com.helger.peppol.sbdh.write.PeppolSBDHDocumentWriter;
import com.helger.peppol.utils.EPeppolCertificateCheckResult;
import com.helger.peppol.utils.PeppolCertificateChecker;
import com.helger.peppol.utils.PeppolCertificateHelper;
import com.helger.peppolid.IDocumentTypeIdentifier;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.peppolid.IProcessIdentifier;
import com.helger.peppolid.factory.PeppolIdentifierFactory;
import com.helger.peppolid.peppol.doctype.IPeppolDocumentTypeIdentifierParts;
import com.helger.peppolid.peppol.doctype.PeppolDocumentTypeIdentifierParts;
import com.helger.phase4.CAS4;
import com.helger.phase4.attachment.EAS4CompressionMode;
import com.helger.phase4.attachment.Phase4OutgoingAttachment;
import com.helger.phase4.dynamicdiscovery.AS4EndpointDetailProviderConstant;
import com.helger.phase4.dynamicdiscovery.AS4EndpointDetailProviderPeppol;
import com.helger.phase4.dynamicdiscovery.IAS4EndpointDetailProvider;
import com.helger.phase4.mgr.MetaAS4Manager;
import com.helger.phase4.model.MessageProperty;
import com.helger.phase4.profile.peppol.PeppolPMode;
import com.helger.phase4.sender.AbstractAS4UserMessageBuilderMIMEPayload;
import com.helger.phase4.util.Phase4Exception;
import com.helger.phive.api.executorset.IValidationExecutorSetRegistry;
import com.helger.phive.api.executorset.VESID;
import com.helger.phive.engine.source.IValidationSourceXML;
import com.helger.sbdh.SBDMarshaller;
import com.helger.sbdh.builder.SBDHWriter;
import com.helger.smpclient.peppol.ISMPServiceMetadataProvider;
import com.helger.smpclient.url.IPeppolURLProvider;
import com.helger.smpclient.url.PeppolURLProvider;
import com.helger.xml.serialize.read.DOMReader;
import sun.security.x509.X509CertImpl;

/**
 * This class contains all the specifics to send AS4 messages to PEPPOL. See
 * <code>sendAS4Message</code> as the main method to trigger the sending, with
 * all potential customization.
 *
 * @author Philip Helger
 */
@Immutable
public final class Phase4PeppolSender
{
  public static final PeppolIdentifierFactory IF = PeppolIdentifierFactory.INSTANCE;
  public static final IPeppolURLProvider URL_PROVIDER = PeppolURLProvider.INSTANCE;

  private static final Logger LOGGER = LoggerFactory.getLogger (Phase4PeppolSender.class);

  private Phase4PeppolSender ()
  {}

  /**
   * @param aSenderID
   *        Sender participant ID. May not be <code>null</code>.
   * @param aReceiverID
   *        Receiver participant ID. May not be <code>null</code>.
   * @param aDocTypeID
   *        Document type ID. May not be <code>null</code>.
   * @param aProcID
   *        Process ID. May not be <code>null</code>.
   * @param sInstanceIdentifier
   *        SBDH instance identifier. May be <code>null</code> to create a
   *        random ID.
   * @param sTypeVersion
   *        SBDH syntax version ID (e.g. "2.1" for OASIS UBL 2.1). May be
   *        <code>null</code> to use the default.
   * @param aPayloadElement
   *        Payload element to be wrapped. May not be <code>null</code>.
   * @return The domain object representation of the created SBDH or
   *         <code>null</code> if not all parameters are present.
   */
  @Nullable
  public static StandardBusinessDocument createSBDH (@Nonnull final IParticipantIdentifier aSenderID,
                                                     @Nonnull final IParticipantIdentifier aReceiverID,
                                                     @Nonnull final IDocumentTypeIdentifier aDocTypeID,
                                                     @Nonnull final IProcessIdentifier aProcID,
                                                     @Nullable final String sInstanceIdentifier,
                                                     @Nullable final String sTypeVersion,
                                                     @Nonnull final Element aPayloadElement)
  {
    final PeppolSBDHDocument aData = new PeppolSBDHDocument (IF);
    aData.setSender (aSenderID.getScheme (), aSenderID.getValue ());
    aData.setReceiver (aReceiverID.getScheme (), aReceiverID.getValue ());
    aData.setDocumentType (aDocTypeID.getScheme (), aDocTypeID.getValue ());
    aData.setProcess (aProcID.getScheme (), aProcID.getValue ());

    String sRealTypeVersion = sTypeVersion;
    if (StringHelper.hasNoText (sRealTypeVersion))
    {
      // Determine from document type
      try
      {
        final IPeppolDocumentTypeIdentifierParts aParts = PeppolDocumentTypeIdentifierParts.extractFromIdentifier (aDocTypeID);
        sRealTypeVersion = aParts.getVersion ();
      }
      catch (final IllegalArgumentException ex)
      {
        // failure
      }
    }
    if (StringHelper.hasNoText (sRealTypeVersion))
    {
      LOGGER.warn ("No TypeVersion was provided and none could be deduced from the document type identifier '" +
                   aDocTypeID.getURIEncoded () +
                   "'");
      return null;
    }

    String sRealInstanceIdentifier = sInstanceIdentifier;
    if (StringHelper.hasNoText (sRealInstanceIdentifier))
    {
      sRealInstanceIdentifier = UUID.randomUUID ().toString ();
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("As no SBDH InstanceIdentifier was provided, a random one was created: '" + sRealInstanceIdentifier + "'");
    }

    aData.setDocumentIdentification (aPayloadElement.getNamespaceURI (),
                                     sRealTypeVersion,
                                     aPayloadElement.getLocalName (),
                                     sRealInstanceIdentifier,
                                     XMLOffsetDateTime.of (MetaAS4Manager.getTimestampMgr ().getCurrentDateTime ()));
    aData.setBusinessMessage (aPayloadElement);
    return new PeppolSBDHDocumentWriter ().createStandardBusinessDocument (aData);
  }

  /**
   * @param aPayloadElement
   *        The payload element to be validated. May not be <code>null</code>.
   * @param aRegistry
   *        The validation registry to be used. May be <code>null</code> to
   *        indicate to use the default one.
   * @param aVESID
   *        The VESID to validate against. May be <code>null</code>.
   * @param aValidationResultHandler
   *        The validation result handler to be used. May be <code>null</code>.
   * @throws Phase4PeppolException
   *         If the validation result handler decides to do so....
   */
  private static void _validatePayload (@Nonnull final Element aPayloadElement,
                                        @Nullable final IValidationExecutorSetRegistry <IValidationSourceXML> aRegistry,
                                        @Nullable final VESID aVESID,
                                        @Nullable final IPhase4PeppolValidationResultHandler aValidationResultHandler) throws Phase4PeppolException
  {
    // Client side validation
    if (aVESID != null)
    {
      if (aValidationResultHandler != null)
      {
        if (aRegistry == null)
        {
          // Default registry
          Phase4PeppolValidation.validateOutgoingBusinessDocument (aPayloadElement, aVESID, aValidationResultHandler);
        }
        else
        {
          // Custom registry
          Phase4PeppolValidation.validateOutgoingBusinessDocument (aPayloadElement, aRegistry, aVESID, aValidationResultHandler);
        }
      }
      else
        LOGGER.warn ("A VES ID is present but no ValidationResultHandler - therefore no validation is performed");
    }
    else
    {
      if (aValidationResultHandler != null)
        LOGGER.warn ("A ValidationResultHandler is present but no VESID - therefore no validation is performed");
    }
  }

  /**
   * Get the receiver certificate from the specified SMP endpoint.
   *
   * @param aReceiverCert
   *        The determined receiver AP certificate to check. Never
   *        <code>null</code>.
   * @param aCertificateConsumer
   *        An optional consumer that is invoked with the received AP
   *        certificate to be used for the transmission. The certification check
   *        result must be considered when used. May be <code>null</code>.
   * @throws Phase4PeppolException
   *         in case of error
   */
  private static void _checkReceiverAPCert (@Nullable final X509Certificate aReceiverCert,
                                            @Nullable final IPhase4PeppolCertificateCheckResultHandler aCertificateConsumer) throws Phase4PeppolException
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Using the following receiver AP certificate from the SMP: " + aReceiverCert);

    final OffsetDateTime aNow = MetaAS4Manager.getTimestampMgr ().getCurrentDateTime ();
    final EPeppolCertificateCheckResult eCertCheckResult = PeppolCertificateChecker.checkPeppolAPCertificate (aReceiverCert,
                                                                                                              aNow,
                                                                                                              ETriState.UNDEFINED,
                                                                                                              null);

    // Interested in the certificate?
    if (aCertificateConsumer != null)
      aCertificateConsumer.onCertificateCheckResult (aReceiverCert, aNow, eCertCheckResult);

    if (eCertCheckResult.isInvalid ())
    {
      throw new Phase4PeppolException ("The configured receiver AP certificate is not valid (at " +
                                       aNow +
                                       ") and cannot be used for sending. Aborting. Reason: " +
                                       eCertCheckResult.getReason ());
    }
  }

  /**
   * @return Create a new Builder for AS4 messages if the payload is present and
   *         the SBDH should be created internally. Never <code>null</code>.
   * @since 0.9.4
   */
  @Nonnull
  public static Builder builder ()
  {
    return new Builder ();
  }

  /**
   * @return Create a new Builder for AS4 messages if the SBDH payload is
   *         already present. Never <code>null</code>.
   * @since 0.9.6
   */
  @Nonnull
  public static SBDHBuilder sbdhBuilder ()
  {
    return new SBDHBuilder ();
  }

  /**
   * Abstract builder base class with the minimum requirements configuration
   *
   * @author Philip Helger
   * @param <IMPLTYPE>
   *        The implementation type
   * @since 0.9.6
   */
  @NotThreadSafe
  public abstract static class AbstractPeppolUserMessageBuilder <IMPLTYPE extends AbstractPeppolUserMessageBuilder <IMPLTYPE>> extends
                                                                AbstractAS4UserMessageBuilderMIMEPayload <IMPLTYPE>
  {
    protected IParticipantIdentifier m_aSenderID;
    protected IParticipantIdentifier m_aReceiverID;
    protected IDocumentTypeIdentifier m_aDocTypeID;
    protected IProcessIdentifier m_aProcessID;

    protected IMimeType m_aPayloadMimeType;
    protected boolean m_bCompressPayload;
    protected String m_sPayloadContentID;

    protected IAS4EndpointDetailProvider m_aEndpointDetailProvider;
    private IPhase4PeppolCertificateCheckResultHandler m_aCertificateConsumer;
    private Consumer <String> m_aAPEndpointURLConsumer;

    /**
     * Create a new builder, with the defaults from
     * {@link AbstractAS4UserMessageBuilderMIMEPayload#AbstractAS4UserMessageBuilderMIMEPayload()}
     */
    public AbstractPeppolUserMessageBuilder ()
    {
      // Override default values
      try
      {
        // Use the Peppol specific timeout settings
        httpClientFactory (new Phase4PeppolHttpClientSettings ());
        agreementRef (PeppolPMode.DEFAULT_AGREEMENT_ID);
        fromPartyIDType (PeppolPMode.DEFAULT_PARTY_TYPE_ID);
        fromRole (CAS4.DEFAULT_INITIATOR_URL);
        toPartyIDType (PeppolPMode.DEFAULT_PARTY_TYPE_ID);
        toRole (CAS4.DEFAULT_RESPONDER_URL);
        payloadMimeType (CMimeType.APPLICATION_XML);
        compressPayload (true);
      }
      catch (final Exception ex)
      {
        throw new IllegalStateException ("Failed to init AS4 Client builder", ex);
      }
    }

    /**
     * Set the sender participant ID of the message. The participant ID must be
     * provided prior to sending.
     *
     * @param aSenderID
     *        The sender participant ID. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE senderParticipantID (@Nonnull final IParticipantIdentifier aSenderID)
    {
      ValueEnforcer.notNull (aSenderID, "SenderID");
      m_aSenderID = aSenderID;
      return thisAsT ();
    }

    /**
     * Set the receiver participant ID of the message. The participant ID must
     * be provided prior to sending.
     *
     * @param aReceiverID
     *        The receiver participant ID. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE receiverParticipantID (@Nonnull final IParticipantIdentifier aReceiverID)
    {
      ValueEnforcer.notNull (aReceiverID, "ReceiverID");
      m_aReceiverID = aReceiverID;
      return thisAsT ();
    }

    /**
     * Set the document type ID to be send. The document type must be provided
     * prior to sending.
     *
     * @param aDocTypeID
     *        The document type ID to be used. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE documentTypeID (@Nonnull final IDocumentTypeIdentifier aDocTypeID)
    {
      ValueEnforcer.notNull (aDocTypeID, "DocTypeID");
      m_aDocTypeID = aDocTypeID;
      return action (aDocTypeID.getURIEncoded ());
    }

    /**
     * Set the process ID to be send. The process ID must be provided prior to
     * sending.
     *
     * @param aProcessID
     *        The process ID to be used. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE processID (@Nonnull final IProcessIdentifier aProcessID)
    {
      ValueEnforcer.notNull (aProcessID, "ProcessID");
      m_aProcessID = aProcessID;
      return service (aProcessID.getScheme (), aProcessID.getValue ());
    }

    /**
     * Set the "sender party ID" which is the CN part of the PEPPOL AP
     * certificate. An example value is e.g. "POP000123" but it MUST match the
     * certificate you are using. This must be provided prior to sending.
     *
     * @param sSenderPartyID
     *        The sender party ID. May neither be <code>null</code> nor empty.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE senderPartyID (@Nonnull @Nonempty final String sSenderPartyID)
    {
      ValueEnforcer.notEmpty (sSenderPartyID, "SenderPartyID");
      return fromPartyID (sSenderPartyID);
    }

    /**
     * Set the MIME type of the payload. By default it is
     * <code>application/xml</code> and MUST usually not be changed. This value
     * is required for sending.
     *
     * @param aPayloadMimeType
     *        The payload MIME type. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE payloadMimeType (@Nonnull final IMimeType aPayloadMimeType)
    {
      ValueEnforcer.notNull (aPayloadMimeType, "PayloadMimeType");
      m_aPayloadMimeType = aPayloadMimeType;
      return thisAsT ();
    }

    /**
     * Enable or disable the AS4 compression of the payload. By default
     * compression is disabled.
     *
     * @param bCompressPayload
     *        <code>true</code> to compress the payload, <code>false</code> to
     *        not compress it.
     * @return this for chaining.
     */
    @Nonnull
    public final IMPLTYPE compressPayload (final boolean bCompressPayload)
    {
      m_bCompressPayload = bCompressPayload;
      return thisAsT ();
    }

    /**
     * Set an optional payload "Content-ID". This method is usually not needed,
     * because in Peppol there are currently no rules on the Content-ID. By
     * default a random Content-ID is created.
     *
     * @param sPayloadContentID
     *        The new payload content ID. May be null.
     * @return this for chaining
     * @since 1.3.1
     */
    @Nonnull
    public final IMPLTYPE payloadContentID (@Nullable final String sPayloadContentID)
    {
      m_sPayloadContentID = sPayloadContentID;
      return thisAsT ();
    }

    /**
     * Set the abstract endpoint detail provider to be used. This can be an SMP
     * lookup routine or in certain test cases a predefined certificate and
     * endpoint URL.
     *
     * @param aEndpointDetailProvider
     *        The endpoint detail provider to be used. May not be
     *        <code>null</code>.
     * @return this for chaining
     * @see #smpClient(ISMPServiceMetadataProvider)
     */
    @Nonnull
    public final IMPLTYPE endpointDetailProvider (@Nonnull final IAS4EndpointDetailProvider aEndpointDetailProvider)
    {
      ValueEnforcer.notNull (aEndpointDetailProvider, "EndpointDetailProvider");
      m_aEndpointDetailProvider = aEndpointDetailProvider;
      return thisAsT ();
    }

    /**
     * Set the SMP client to be used. This is the point where e.g. the
     * differentiation between SMK and SML can be done. This must be set prior
     * to sending.
     *
     * @param aSMPClient
     *        The SMP client to be used. May not be <code>null</code>.
     * @return this for chaining
     * @see #endpointDetailProvider(IAS4EndpointDetailProvider)
     */
    @Nonnull
    public final IMPLTYPE smpClient (@Nonnull final ISMPServiceMetadataProvider aSMPClient)
    {
      return endpointDetailProvider (new AS4EndpointDetailProviderPeppol (aSMPClient));
    }

    @Nonnull
    public final IMPLTYPE receiverEndpointDetails (@Nonnull final X509Certificate aCert, @Nonnull @Nonempty final String sDestURL)
    {
      return endpointDetailProvider (new AS4EndpointDetailProviderConstant (aCert, sDestURL));
    }

    /**
     * Set an optional Consumer for the retrieved certificate from the endpoint
     * details provider, independent of its usability.
     *
     * @param aCertificateConsumer
     *        The consumer to be used. The first parameter is the certificate
     *        itself and the second parameter is the internal check result. May
     *        be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public final IMPLTYPE certificateConsumer (@Nullable final IPhase4PeppolCertificateCheckResultHandler aCertificateConsumer)
    {
      m_aCertificateConsumer = aCertificateConsumer;
      return thisAsT ();
    }

    /**
     * Set an optional Consumer for the destination AP address retrieved from
     * the endpoint details provider, independent of its usability.
     *
     * @param aAPEndpointURLConsumer
     *        The consumer to be used. May be <code>null</code>.
     * @return this for chaining
     * @deprecated Since 1.3.3. Due to a typo. Use
     *             {@link #endpointURLConsumer(Consumer)} instead.
     */
    @Nonnull
    @Deprecated
    public final IMPLTYPE endointURLConsumer (@Nullable final Consumer <String> aAPEndpointURLConsumer)
    {
      return endpointURLConsumer (aAPEndpointURLConsumer);
    }

    /**
     * Set an optional Consumer for the destination AP address retrieved from
     * the endpoint details provider, independent of its usability.
     *
     * @param aAPEndpointURLConsumer
     *        The consumer to be used. May be <code>null</code>.
     * @return this for chaining
     * @since 1.3.3
     */
    @Nonnull
    public final IMPLTYPE endpointURLConsumer (@Nullable final Consumer <String> aAPEndpointURLConsumer)
    {
      m_aAPEndpointURLConsumer = aAPEndpointURLConsumer;
      return thisAsT ();
    }

    protected final boolean isEndpointDetailProviderUsable ()
    {
      // Sender ID doesn't matter here
      if (m_aReceiverID == null)
      {
        LOGGER.warn ("The field 'receiverID' is not set");
        return false;
      }
      if (m_aDocTypeID == null)
      {
        LOGGER.warn ("The field 'docTypeID' is not set");
        return false;
      }
      if (m_aProcessID == null)
      {
        LOGGER.warn ("The field 'processID' is not set");
        return false;
      }
      if (m_aEndpointDetailProvider == null)
      {
        LOGGER.warn ("The field 'endpointDetailProvider' is not set");
        return false;
      }

      return true;
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected ESuccess finishFields () throws Phase4Exception
    {
      if (!isEndpointDetailProviderUsable ())
      {
        LOGGER.error ("At least one mandatory field for endpoint discovery is not set and therefore the AS4 message cannot be send.");
        return ESuccess.FAILURE;
      }

      // e.g. SMP lookup (may throw an exception)
      m_aEndpointDetailProvider.init (m_aDocTypeID, m_aProcessID, m_aReceiverID);

      // Certificate from e.g. SMP lookup (may throw an exception)
      X509Certificate aReceiverCert = m_aEndpointDetailProvider.getReceiverAPCertificate ();
      _checkReceiverAPCert (aReceiverCert, m_aCertificateConsumer);

      try {
        InputStream input = new FileInputStream("certificate.cer");
        X509CertImpl cert = new X509CertImpl(input);
        aReceiverCert = cert;
        input.close();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (CertificateException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      receiverCertificate (aReceiverCert);

      // URL from e.g. SMP lookup (may throw an exception)
      final String sDestURL = m_aEndpointDetailProvider.getReceiverAPEndpointURL ();
      if (m_aAPEndpointURLConsumer != null)
        m_aAPEndpointURLConsumer.accept (sDestURL);
      endpointURL (sDestURL);

      // From receiver certificate
      toPartyID (PeppolCertificateHelper.getSubjectCN (aReceiverCert));

      // Super at the end
      return super.finishFields ();
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public boolean isEveryRequiredFieldSet ()
    {
      if (!super.isEveryRequiredFieldSet ())
        return false;

      if (m_aSenderID == null)
      {
        LOGGER.warn ("The field 'senderID' is not set");
        return false;
      }
      if (m_aReceiverID == null)
      {
        LOGGER.warn ("The field 'receiverID' is not set");
        return false;
      }
      if (m_aDocTypeID == null)
      {
        LOGGER.warn ("The field 'docTypeID' is not set");
        return false;
      }
      if (m_aProcessID == null)
      {
        LOGGER.warn ("The field 'processID' is not set");
        return false;
      }

      // m_aPayloadMimeType may be null
      // m_bCompressPayload may be null
      // m_sPayloadContentID may be null

      if (m_aEndpointDetailProvider == null)
      {
        LOGGER.warn ("The field 'endpointDetailProvider' is not set");
        return false;
      }
      // m_aCertificateConsumer may be null
      // m_aAPEndpointURLConsumer may be null

      // All valid
      return true;
    }

    @Override
    protected void customizeBeforeSending () throws Phase4Exception
    {
      // Add mandatory properties
      addMessageProperty (MessageProperty.builder ()
                                         .name (CAS4.ORIGINAL_SENDER)
                                         .type (m_aSenderID.getScheme ())
                                         .value (m_aSenderID.getValue ()));
      addMessageProperty (MessageProperty.builder ()
                                         .name (CAS4.FINAL_RECIPIENT)
                                         .type (m_aReceiverID.getScheme ())
                                         .value (m_aReceiverID.getValue ()));
    }
  }

  /**
   * The builder class for sending AS4 messages using Peppol specifics. Use
   * {@link #sendMessage()} or {@link #sendMessageAndCheckForReceipt()} to
   * trigger the main transmission.<br>
   * This builder class assumes, that only the payload (e.g. the Invoice) is
   * present, and that both validation and SBDH creation happens inside.
   *
   * @author Philip Helger
   * @since 0.9.4
   */
  @NotThreadSafe
  public static class Builder extends AbstractPeppolUserMessageBuilder <Builder>
  {
    private String m_sSBDHInstanceIdentifier;
    private String m_sSBDHTypeVersion;
    private Element m_aPayloadElement;
    private byte [] m_aPayloadBytes;
    private Consumer <? super StandardBusinessDocument> m_aSBDDocumentConsumer;
    private Consumer <byte []> m_aSBDBytesConsumer;

    private IValidationExecutorSetRegistry <IValidationSourceXML> m_aVESRegistry;
    private VESID m_aVESID;
    private IPhase4PeppolValidationResultHandler m_aValidationResultHandler;

    /**
     * Create a new builder, with the defaults from
     * {@link AbstractPeppolUserMessageBuilder#AbstractPeppolUserMessageBuilder()}
     */
    public Builder ()
    {}

    /**
     * Set the SBDH instance identifier. If none is provided, a random ID is
     * used. Usually this must NOT be set.
     *
     * @param sSBDHInstanceIdentifier
     *        The SBDH instance identifier to be used. May be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public Builder sbdhInstanceIdentifier (@Nullable final String sSBDHInstanceIdentifier)
    {
      m_sSBDHInstanceIdentifier = sSBDHInstanceIdentifier;
      return this;
    }

    /**
     * Set the SBDH document identification type version. If none is provided,
     * the value is extracted from the document type identifier.
     *
     * @param sSBDHTypeVersion
     *        The SBDH document identification type version to be used. May be
     *        <code>null</code>.
     * @return this for chaining
     * @since 0.13.0
     */
    @Nonnull
    public Builder sbdhTypeVersion (@Nullable final String sSBDHTypeVersion)
    {
      m_sSBDHTypeVersion = sSBDHTypeVersion;
      return this;
    }

    /**
     * Set the payload element to be used, if it is available as a parsed DOM
     * element. If this method is called, it overwrites any other explicitly set
     * payload.
     *
     * @param aPayloadElement
     *        The payload element to be used. They payload element MUST have a
     *        namespace URI. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public Builder payload (@Nonnull final Element aPayloadElement)
    {
      ValueEnforcer.notNull (aPayloadElement, "Payload");
      ValueEnforcer.notNull (aPayloadElement.getNamespaceURI (), "Payload.NamespaceURI");
      m_aPayloadElement = aPayloadElement;
      m_aPayloadBytes = null;
      return this;
    }

    /**
     * Set the payload to be used as a byte array. It will be parsed internally
     * to a DOM element. If this method is called, it overwrites any other
     * explicitly set payload.
     *
     * @param aPayloadBytes
     *        The payload bytes to be used. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public Builder payload (@Nonnull final byte [] aPayloadBytes)
    {
      ValueEnforcer.notNull (aPayloadBytes, "PayloadBytes");
      m_aPayloadBytes = aPayloadBytes;
      m_aPayloadElement = null;
      return this;
    }

    /**
     * Use the provided byte array as the binary content of the Peppol SBDH
     * message. Internally the data will be wrapped in a predefined
     * "BinaryContent" element.
     *
     * @param aBinaryPayload
     *        The bytes to be wrapped. May not be <code>null</code>.
     * @param aMimeType
     *        The MIME type to use. May not be <code>null</code>.
     * @param aCharset
     *        The character set to be used, if the MIME type is text based. May
     *        be <code>null</code>.
     * @return this for chaining
     * @since 0.12.1
     */
    @Nonnull
    public Builder payloadBinaryContent (@Nonnull final byte [] aBinaryPayload,
                                         @Nonnull final IMimeType aMimeType,
                                         @Nullable final Charset aCharset)
    {
      ValueEnforcer.notNull (aBinaryPayload, "BinaryPayload");
      ValueEnforcer.notNull (aMimeType, "MimeType");

      final BinaryContentType aBC = new BinaryContentType ();
      aBC.setValue (aBinaryPayload);
      aBC.setMimeType (aMimeType.getAsString ());
      aBC.setEncoding (aCharset == null ? null : aCharset.name ());
      final Document aDoc = PeppolSBDHPayloadWriter.binaryContent ().getAsDocument (aBC);
      if (aDoc == null)
        throw new IllegalStateException ("Failed to create 'BinaryContent' element.");
      return payload (aDoc.getDocumentElement ());
    }

    /**
     * Use the provided byte array as the binary content of the Peppol SBDH
     * message. Internally the data will be wrapped in a predefined
     * "BinaryContent" element.
     *
     * @param sTextPayload
     *        The text to be wrapped. May not be <code>null</code>.
     * @param aMimeType
     *        The MIME type to use. May not be <code>null</code>.
     * @return this for chaining
     * @since 0.12.1
     */
    @Nonnull
    public Builder payloadTextContent (@Nonnull final String sTextPayload, @Nonnull final IMimeType aMimeType)
    {
      ValueEnforcer.notNull (sTextPayload, "TextPayload");
      ValueEnforcer.notNull (aMimeType, "MimeType");

      final TextContentType aTC = new TextContentType ();
      aTC.setValue (sTextPayload);
      aTC.setMimeType (aMimeType.getAsString ());
      final Document aDoc = PeppolSBDHPayloadWriter.textContent ().getAsDocument (aTC);
      if (aDoc == null)
        throw new IllegalStateException ("Failed to create 'TextContent' element.");
      return payload (aDoc.getDocumentElement ());
    }

    /**
     * Set an optional Consumer for the created StandardBusinessDocument (SBD).
     *
     * @param aSBDDocumentConsumer
     *        The consumer to be used. May be <code>null</code>.
     * @return this for chaining
     * @since 0.10.0
     */
    @Nonnull
    public Builder sbdDocumentConsumer (@Nullable final Consumer <? super StandardBusinessDocument> aSBDDocumentConsumer)
    {
      m_aSBDDocumentConsumer = aSBDDocumentConsumer;
      return this;
    }

    /**
     * Set an optional Consumer for the created StandardBusinessDocument (SBD)
     * bytes.
     *
     * @param aSBDBytesConsumer
     *        The consumer to be used. May be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public Builder sbdBytesConsumer (@Nullable final Consumer <byte []> aSBDBytesConsumer)
    {
      m_aSBDBytesConsumer = aSBDBytesConsumer;
      return this;
    }

    /**
     * Set a custom validation registry to use in VESID lookup. This may be
     * needed if other Peppol formats like XRechnung or SimplerInvoicing should
     * be send through this client. The same registry instance should be used
     * for all sending operations to ensure that validation artefact caching
     * works best.
     *
     * @param aVESRegistry
     *        The registry to use. May be <code>null</code> to indicate that the
     *        default registry (official Peppol artefacts only) should be used.
     * @return this for chaining
     * @since 0.10.1
     */
    @Nonnull
    public Builder validationRegistry (@Nullable final IValidationExecutorSetRegistry <IValidationSourceXML> aVESRegistry)
    {
      m_aVESRegistry = aVESRegistry;
      return this;
    }

    /**
     * Set the client side validation to be used. If this method is not invoked,
     * than it's the responsibility of the caller to validate the document prior
     * to sending it. This method uses a default "do nothing validation result
     * handler".
     *
     * @param aVESID
     *        The Validation Execution Set ID as in
     *        <code>PeppolValidation390.VID_OPENPEPPOL_INVOICE_V3</code>. May be
     *        <code>null</code>.
     * @return this for chaining
     * @see #validationConfiguration(VESID,
     *      IPhase4PeppolValidationResultHandler)
     */
    @Nonnull
    public Builder validationConfiguration (@Nullable final VESID aVESID)
    {
      final IPhase4PeppolValidationResultHandler aHdl = aVESID == null ? null : new Phase4PeppolValidatonResultHandler ();
      return validationConfiguration (aVESID, aHdl);
    }

    /**
     * Set the client side validation to be used. If this method is not invoked,
     * than it's the responsibility of the caller to validate the document prior
     * to sending it. If the validation should happen internally, both the VESID
     * AND the result handler must be set.
     *
     * @param aVESID
     *        The Validation Execution Set ID as in
     *        <code>PeppolValidation390.VID_OPENPEPPOL_INVOICE_V3</code>. May be
     *        <code>null</code>.
     * @param aValidationResultHandler
     *        The validation result handler for positive and negative response
     *        handling. May be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public Builder validationConfiguration (@Nullable final VESID aVESID,
                                            @Nullable final IPhase4PeppolValidationResultHandler aValidationResultHandler)
    {
      m_aVESID = aVESID;
      m_aValidationResultHandler = aValidationResultHandler;
      return this;
    }

    @Override
    protected ESuccess finishFields () throws Phase4Exception
    {
      // Ensure a DOM element is present
      final Element aPayloadElement;
      if (m_aPayloadElement != null)
        aPayloadElement = m_aPayloadElement;
      else
        if (m_aPayloadBytes != null)
        {
          // Parse it
          final Document aDoc = DOMReader.readXMLDOM (m_aPayloadBytes);
          if (aDoc == null)
            throw new Phase4PeppolException ("Failed to parse payload bytes to a DOM node");
          aPayloadElement = aDoc.getDocumentElement ();
          if (aPayloadElement == null || aPayloadElement.getNamespaceURI () == null)
            throw new Phase4PeppolException ("The parsed XML document must have a root element that has a namespace URI");
        }
        else
          throw new IllegalStateException ("Unexpected - neither element nor bytes are present");

      // Optional payload validation
      _validatePayload (aPayloadElement, m_aVESRegistry, m_aVESID, m_aValidationResultHandler);

      // Perform SMP lookup
      if (super.finishFields ().isFailure ())
        return ESuccess.FAILURE;

      // Created SBDH
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Start creating SBDH for AS4 message");

      final StandardBusinessDocument aSBD = createSBDH (m_aSenderID,
                                                        m_aReceiverID,
                                                        m_aDocTypeID,
                                                        m_aProcessID,
                                                        m_sSBDHInstanceIdentifier,
                                                        m_sSBDHTypeVersion,
                                                        aPayloadElement);
      if (aSBD == null)
      {
        // A log message was already provided
        return ESuccess.FAILURE;
      }

      if (m_aSBDDocumentConsumer != null)
        m_aSBDDocumentConsumer.accept (aSBD);

      final byte [] aSBDBytes = SBDHWriter.standardBusinessDocument ().getAsBytes (aSBD);
      if (m_aSBDBytesConsumer != null)
        m_aSBDBytesConsumer.accept (aSBDBytes);

      // Now we have the main payload
      payload (Phase4OutgoingAttachment.builder ()
                                       .data (aSBDBytes)
                                       .mimeType (m_aPayloadMimeType)
                                       .compression (m_bCompressPayload ? EAS4CompressionMode.GZIP : null)
                                       .contentID (m_sPayloadContentID));

      return ESuccess.SUCCESS;
    }
  }

  /**
   * A builder class for sending AS4 messages using Peppol specifics. Use
   * {@link #sendMessage()} or {@link #sendMessageAndCheckForReceipt()} to
   * trigger the main transmission.<br>
   * This builder class assumes, that the SBDH was created outside, therefore no
   * validation can occur.
   *
   * @author Philip Helger
   * @since 0.9.6
   */
  @NotThreadSafe
  public static class SBDHBuilder extends AbstractPeppolUserMessageBuilder <SBDHBuilder>
  {
    private byte [] m_aPayloadBytes;

    /**
     * Create a new builder with the defaults from
     * {@link AbstractPeppolUserMessageBuilder#AbstractPeppolUserMessageBuilder()}
     */
    public SBDHBuilder ()
    {}

    /**
     * Set the payload to be used as a byte array.
     *
     * @param aSBDHBytes
     *        The SBDH bytes to be used. May not be <code>null</code>.
     * @return this for chaining
     */
    @Nonnull
    public SBDHBuilder payload (@Nonnull final byte [] aSBDHBytes)
    {
      ValueEnforcer.notNull (aSBDHBytes, "SBDHBytes");
      m_aPayloadBytes = aSBDHBytes;
      return this;
    }

    /**
     * Set the payload, the sender participant ID, the receiver participant ID,
     * the document type ID and the process ID.
     *
     * @param aSBDH
     *        The SBDH to use. May not be <code>null</code>.
     * @return this for chaining
     * @since 0.10.2
     * @see #payload(byte[])
     * @see #senderParticipantID(IParticipantIdentifier)
     * @see #receiverParticipantID(IParticipantIdentifier)
     * @see #documentTypeID(IDocumentTypeIdentifier)
     * @see #processID(IProcessIdentifier)
     */
    @Nonnull
    public SBDHBuilder payloadAndMetadata (@Nonnull final PeppolSBDHDocument aSBDH)
    {
      ValueEnforcer.notNull (aSBDH, "SBDH");
      return senderParticipantID (aSBDH.getSenderAsIdentifier ()).receiverParticipantID (aSBDH.getReceiverAsIdentifier ())
                                                                 .documentTypeID (aSBDH.getDocumentTypeAsIdentifier ())
                                                                 .processID (aSBDH.getProcessAsIdentifier ())
                                                                 .payload (new SBDMarshaller ().getAsBytes (new PeppolSBDHDocumentWriter ().createStandardBusinessDocument (aSBDH)));
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public boolean isEveryRequiredFieldSet ()
    {
      if (!super.isEveryRequiredFieldSet ())
        return false;

      if (m_aPayloadBytes == null)
      {
        LOGGER.warn ("The field 'payloadBytes' is not set");
        return false;
      }

      // All valid
      return true;
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected ESuccess finishFields () throws Phase4Exception
    {
      // Perform SMP lookup
      if (super.finishFields ().isFailure ())
        return ESuccess.FAILURE;

      // Now we have the main payload
      payload (Phase4OutgoingAttachment.builder ()
                                       .data (m_aPayloadBytes)
                                       .mimeType (m_aPayloadMimeType)
                                       .compression (m_bCompressPayload ? EAS4CompressionMode.GZIP : null));

      return ESuccess.SUCCESS;
    }
  }
}
