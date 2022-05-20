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
package com.helger.phase4.client;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Nonnull;
import javax.annotation.WillNotClose;

import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.io.resource.ClassPathResource;
import com.helger.commons.mime.CMimeType;
import com.helger.phase4.AS4TestConstants;
import com.helger.phase4.CAS4;
import com.helger.phase4.attachment.EAS4CompressionMode;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.AS4CryptoProperties;
import com.helger.phase4.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase4.crypto.ECryptoAlgorithmSign;
import com.helger.phase4.crypto.ECryptoAlgorithmSignDigest;
import com.helger.phase4.messaging.domain.MessageHelperMethods;
import com.helger.phase4.mgr.MetaAS4Manager;
import com.helger.phase4.profile.cef.AS4CEFProfileRegistarSPI;
import com.helger.phase4.server.AbstractAS4TestSetUp;
import com.helger.phase4.server.MockJettySetup;
import com.helger.phase4.soap.ESoapVersion;
import com.helger.phase4.util.AS4ResourceHelper;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.serialize.MicroWriter;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Test class for class {@link AS4ClientUserMessage}
 *
 * @author Martin Bayerl
 * @author Philip Helger
 */
public final class AS4ClientUserMessageTest extends AbstractAS4TestSetUp
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS4ClientUserMessageTest.class);
  private static final String DEFAULT_AGREEMENT = "urn:as4:agreements:so-that-we-have-a-non-empty-value";
  private static final String SERVER_URL = MockJettySetup.getServerAddressFromSettings ();

  @WillNotClose
  private static AS4ResourceHelper s_aResMgr;

  @BeforeClass
  public static void beforeClass () throws Exception
  {
    MockJettySetup.startServer ();
    s_aResMgr = MockJettySetup.getResourceManagerInstance ();
    MetaAS4Manager.getProfileMgr ().setDefaultProfileID (AS4CEFProfileRegistarSPI.AS4_PROFILE_ID_FOUR_CORNER);
  }

  @AfterClass
  public static void afterClass () throws Exception
  {
    s_aResMgr = null;
    MockJettySetup.shutDownServer ();
  }

  /**
   * To reduce the amount of code in each test, this method sets the basic
   * attributes that are needed for a successful message to build. <br>
   * Only needed for positive messages.
   *
   * @return the AS4Client with the set attributes to continue
   */
  @Nonnull
  private static AS4ClientUserMessage _createMandatoryAttributesSuccessMessage ()
  {
    final AS4ClientUserMessage aClient = new AS4ClientUserMessage (s_aResMgr);
    aClient.setSoapVersion (ESoapVersion.SOAP_12);

    final String sSenderID = "MyPartyIDforSending";
    final String sResponderID = "MyPartyIDforReceving";

    // Use a pmode that you know is currently running on the server your trying
    // to send the message too
    aClient.setAction ("AnAction");
    aClient.setServiceType ("MyServiceType");
    aClient.setServiceValue ("OrderPaper");
    aClient.setConversationID (MessageHelperMethods.createRandomConversationID ());
    aClient.setAgreementRefValue (DEFAULT_AGREEMENT);
    aClient.setFromRole (CAS4.DEFAULT_ROLE);
    aClient.setFromPartyID (sSenderID);
    aClient.setToRole (CAS4.DEFAULT_ROLE);
    aClient.setToPartyID (sResponderID);
    aClient.ebms3Properties ().setAll (AS4TestConstants.getEBMSProperties ());

    return aClient;
  }

  /**
   * Sets the keystore attributes, it uses the dummy keystore
   * keys/dummy-pw-test.jks
   *
   * @param aClient
   *        the client on which these attributes should be set
   * @return the client to continue working with it
   */
  @Nonnull
  private static AS4ClientUserMessage _setKeyStoreTestData (@Nonnull final AS4ClientUserMessage aClient)
  {
    final AS4CryptoProperties aCP = new AS4CryptoProperties ().setKeyStoreType (EKeyStoreType.JKS)
                                                              .setKeyStorePath ("keys/dummy-pw-test.jks")
                                                              .setKeyStorePassword ("test")
                                                              .setKeyAlias ("ph-as4")
                                                              .setKeyPassword ("test");
    aClient.setAS4CryptoFactory (new AS4CryptoFactoryProperties (aCP));
    aClient.cryptParams ().setAlias (aCP.getKeyAlias ());
    return aClient;
  }

  private static void _ensureInvalidState (@Nonnull final AS4ClientUserMessage aClient)
  {
    try
    {
      aClient.buildMessage ("bla", null);
      fail ();
    }
    catch (final Exception ex)
    {
      // expected
    }
  }

  private static void _ensureValidState (@Nonnull final AS4ClientUserMessage aClient)
  {
    try
    {
      aClient.buildMessage ("bla", null);
      // expected
    }
    catch (final Exception ex)
    {
      fail ();
    }
  }

  @Test
  public void testBuildMessageMandatoryCheckFailure () throws Exception
  {
    final AS4ClientUserMessage aClient = new AS4ClientUserMessage (s_aResMgr);
    _ensureInvalidState (aClient);
    aClient.setAction ("AnAction");
    _ensureInvalidState (aClient);
    aClient.setServiceType ("MyServiceType");
    _ensureInvalidState (aClient);
    aClient.setServiceValue ("OrderPaper");
    _ensureInvalidState (aClient);
    aClient.setConversationID (MessageHelperMethods.createRandomConversationID ());
    _ensureInvalidState (aClient);
    aClient.setAgreementRefValue (DEFAULT_AGREEMENT);
    _ensureInvalidState (aClient);
    aClient.setFromRole (CAS4.DEFAULT_ROLE);
    _ensureInvalidState (aClient);
    aClient.setFromPartyID ("MyPartyIDforSending");
    _ensureInvalidState (aClient);
    aClient.setToRole (CAS4.DEFAULT_ROLE);
    _ensureInvalidState (aClient);
    aClient.setToPartyID ("MyPartyIDforReceving");
    // From now on the message is valid
    _ensureValidState (aClient);
    aClient.ebms3Properties ().setAll (new CommonsArrayList <> ());
    _ensureValidState (aClient);
    aClient.ebms3Properties ().setAll (AS4TestConstants.getEBMSProperties ());
    _ensureValidState (aClient);
  }

  @Test
  public void testBuildMessageKeyStoreCheckFailure () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();

    // Set sign attributes, to get to the check, the check only gets called if
    // sign or encrypt needs to be done for the usermessage
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    // No Keystore attributes set
    _ensureInvalidState (aClient);
    final AS4CryptoProperties aCP = new AS4CryptoProperties ().setKeyStorePath ("keys/dummy-pw-test.jks");
    final AS4CryptoFactoryProperties aCF = new AS4CryptoFactoryProperties (aCP);
    aClient.setAS4CryptoFactory (aCF);
    _ensureInvalidState (aClient);
    aCF.cryptoProperties ().setKeyStorePassword ("test");
    _ensureInvalidState (aClient);
    aCF.cryptoProperties ().setKeyStoreType (EKeyStoreType.JKS);
    _ensureInvalidState (aClient);
    aCF.cryptoProperties ().setKeyAlias ("ph-as4");
    _ensureInvalidState (aClient);
    aCF.cryptoProperties ().setKeyPassword ("test");
    _ensureValidState (aClient);
  }

  @Test
  public void testSendBodyPayloadMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.setPayload (DOMReader.readXMLDOM (new ClassPathResource (AS4TestConstants.TEST_PAYLOAD_XML)));
    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendBodyPayloadSignedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.setPayload (DOMReader.readXMLDOM (new ClassPathResource (AS4TestConstants.TEST_PAYLOAD_XML)));

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendBodyPayloadEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.setPayload (DOMReader.readXMLDOM (new ClassPathResource (AS4TestConstants.TEST_PAYLOAD_XML)));

    // Keystore
    _setKeyStoreTestData (aClient);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendBodyPayloadSignedEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.setPayload (DOMReader.readXMLDOM (new ClassPathResource (AS4TestConstants.TEST_PAYLOAD_XML)));

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendOneAttachmentSignedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendOneAttachmentEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    LOGGER.info (MicroWriter.getNodeAsString (aDoc));
  }

  @Test
  public void testSendOneAttachmentSignedEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendManyAttachmentSignedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML2_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_TEST_IMG_JPG).getAsFile (),
                           CMimeType.IMAGE_JPG,
                           (EAS4CompressionMode) null);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendManyAttachmentEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML2_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_TEST_IMG_JPG).getAsFile (),
                           CMimeType.IMAGE_JPG,
                           (EAS4CompressionMode) null);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendManyAttachmentSignedEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML2_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           (EAS4CompressionMode) null);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_TEST_IMG_JPG).getAsFile (),
                           CMimeType.IMAGE_JPG,
                           (EAS4CompressionMode) null);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendOneAttachmentCompressedSignedEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           EAS4CompressionMode.GZIP);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML2_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           EAS4CompressionMode.GZIP);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_TEST_IMG_JPG).getAsFile (),
                           CMimeType.IMAGE_JPG,
                           EAS4CompressionMode.GZIP);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testSendManyAttachmentCompressedSignedEncryptedMessageSuccessful () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           EAS4CompressionMode.GZIP);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_SHORTXML2_XML).getAsFile (),
                           CMimeType.APPLICATION_XML,
                           EAS4CompressionMode.GZIP);
    aClient.addAttachment (new ClassPathResource (AS4TestConstants.ATTACHMENT_TEST_IMG_JPG).getAsFile (),
                           CMimeType.IMAGE_JPG,
                           EAS4CompressionMode.GZIP);

    // Keystore
    _setKeyStoreTestData (aClient);

    // Sign specific
    aClient.signingParams ()
           .setAlgorithmSign (ECryptoAlgorithmSign.RSA_SHA_256)
           .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.DIGEST_SHA_256);

    // Encrypt specific
    aClient.cryptParams ().setAlgorithmCrypt (ECryptoAlgorithmCrypt.AES_128_GCM);

    final IMicroDocument aDoc = aClient.sendMessageAndGetMicroDocument (SERVER_URL);
    assertTrue (MicroWriter.getNodeAsString (aDoc).contains (AS4TestConstants.RECEIPT_ASSERTCHECK));
  }

  @Test
  public void testBuildMessageWithOwnPrefix () throws Exception
  {
    final AS4ClientUserMessage aClient = _createMandatoryAttributesSuccessMessage ();
    final String sMessageIDPrefix = "ThisIsANewPrefixForTestingPurpose@";
    aClient.setMessageIDFactory ( () -> sMessageIDPrefix + MessageHelperMethods.createRandomMessageID ());
    final String sMessageID = aClient.createMessageID ();

    assertTrue (EntityUtils.toString (aClient.buildMessage (sMessageID, null).getHttpEntity ()).contains (sMessageIDPrefix));
  }
}
