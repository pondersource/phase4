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

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.io.resource.ClassPathResource;
import com.helger.commons.mime.CMimeType;
import com.helger.httpclient.HttpClientFactory;
import com.helger.httpclient.HttpClientSettings;
import com.helger.phase4.attachment.Phase4OutgoingAttachment;
import com.helger.phase4.attachment.WSS4JAttachment;
import com.helger.phase4.crypto.AS4CryptParams;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.AS4SigningParams;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.phase4.http.HttpMimeMessageEntity;
import com.helger.phase4.http.HttpXMLEntity;
import com.helger.phase4.messaging.crypto.AS4Encryptor;
import com.helger.phase4.messaging.crypto.AS4Signer;
import com.helger.phase4.messaging.domain.AS4UserMessage;
import com.helger.phase4.messaging.domain.MessageHelperMethods;
import com.helger.phase4.messaging.mime.AS4MimeMessage;
import com.helger.phase4.messaging.mime.MimeMessageCreator;
import com.helger.phase4.soap.ESoapVersion;
import com.helger.phase4.util.AS4ResourceHelper;
import com.helger.xml.serialize.read.DOMReader;

public final class MainOldAS4Client
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MainOldAS4Client.class);

  private MainOldAS4Client ()
  {}

  public static Document getSoapEnvelope11ForTest (@Nonnull final String sPath) throws SAXException,
                                                                                IOException,
                                                                                ParserConfigurationException
  {
    final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance ();
    domFactory.setNamespaceAware (true); // never forget this!
    final DocumentBuilder builder = domFactory.newDocumentBuilder ();
    return builder.parse (new ClassPathResource (sPath).getInputStream ());
  }

  /**
   * Starting point for the SAAJ - SOAP Client Testing
   *
   * @param args
   *        ignored
   */
  public static void main (final String [] args)
  {
    try (final AS4ResourceHelper aResHelper = new AS4ResourceHelper ())
    {
      String sURL = "http://127.0.0.1:8080/as4";
      if (false)
        sURL = "http://msh.holodeck-b2b.org:8080/msh";

      // Deactivate if not sending to local holodeck
      if (false)
        sURL = "http://localhost:8080/msh/";

      final HttpClientSettings aHCS = new HttpClientSettings ();
      if (sURL.startsWith ("https"))
        aHCS.setSSLContextTrustAll ();
      if (false)
      {
        aHCS.setProxyHost (new HttpHost ("172.30.9.6", 8080));
        aHCS.addNonProxyHostsFromPipeString ("localhost|127.0.0.1");
      }
      final CloseableHttpClient aClient = new HttpClientFactory (aHCS).createHttpClient ();

      LOGGER.info ("Sending to " + sURL);
      final HttpPost aPost = new HttpPost (sURL);

      final ICommonsList <WSS4JAttachment> aAttachments = new CommonsArrayList <> ();
      final Node aPayload = DOMReader.readXMLDOM (new ClassPathResource ("SOAPBodyPayload.xml"));
      final ESoapVersion eSoapVersion = ESoapVersion.SOAP_12;
      final IAS4CryptoFactory aCryptoFactory = AS4CryptoFactoryProperties.getDefaultInstance ();

      if (true)
      {
        // No Mime Message Not signed or encrypted, just SOAP + Payload in SOAP
        // -
        // Body
        // final Document aDoc = TestMessages.testSignedUserMessage
        // (ESOAPVersion.SOAP_11, aPayload, aAttachments);
        final AS4UserMessage aMsg = MockClientMessages.createUserMessageNotSigned (eSoapVersion, aPayload, aAttachments);
        final Document aDoc = aMsg.getAsSoapDocument (aPayload);
        aPost.setEntity (new HttpXMLEntity (aDoc, eSoapVersion.getMimeType ()));
      }
      else
        if (false)
        {
          // BodyPayload SIGNED
          final Document aDoc = MockClientMessages.createUserMessageSigned (eSoapVersion, aPayload, aAttachments, aResHelper);
          aPost.setEntity (new HttpXMLEntity (aDoc, eSoapVersion.getMimeType ()));
        }
        else
          if (false)
          {
            // BodyPayload ENCRYPTED
            final AS4UserMessage aMsg = MockClientMessages.createUserMessageNotSigned (eSoapVersion, aPayload, aAttachments);
            Document aDoc = aMsg.getAsSoapDocument (aPayload);
            aDoc = AS4Encryptor.encryptSoapBodyPayload (aCryptoFactory,
                                                        eSoapVersion,
                                                        aDoc,
                                                        false,
                                                        AS4CryptParams.createDefault ().setAlias ("dummy"));

            aPost.setEntity (new HttpXMLEntity (aDoc, eSoapVersion.getMimeType ()));
          }
          else
            if (true)
            {
              aAttachments.add (WSS4JAttachment.createOutgoingFileAttachment (Phase4OutgoingAttachment.builder ()
                                                                                                      .data (ClassPathResource.getAsFile ("attachment/test.xml.gz"))
                                                                                                      .mimeType (CMimeType.APPLICATION_GZIP)
                                                                                                      .build (),
                                                                              aResHelper));
              final AS4UserMessage aMsg = MockClientMessages.createUserMessageNotSigned (eSoapVersion, null, aAttachments);
              final AS4MimeMessage aMimeMsg = MimeMessageCreator.generateMimeMessage (eSoapVersion,
                                                                                      AS4Signer.createSignedMessage (aCryptoFactory,
                                                                                                                     aMsg.getAsSoapDocument (null),
                                                                                                                     eSoapVersion,
                                                                                                                     aMsg.getMessagingID (),
                                                                                                                     aAttachments,
                                                                                                                     aResHelper,
                                                                                                                     false,
                                                                                                                     AS4SigningParams.createDefault ()),
                                                                                      aAttachments);

              // Move all global mime headers to the POST request
              MessageHelperMethods.forEachHeaderAndRemoveAfterwards (aMimeMsg, aPost::addHeader, true);
              aPost.setEntity (new HttpMimeMessageEntity (aMimeMsg));
            }
            else
              if (false)
              {
                Document aDoc = MockClientMessages.createUserMessageSigned (eSoapVersion, aPayload, aAttachments, aResHelper);
                aDoc = AS4Encryptor.encryptSoapBodyPayload (aCryptoFactory,
                                                            eSoapVersion,
                                                            aDoc,
                                                            false,
                                                            AS4CryptParams.createDefault ().setAlias ("dummy"));
                aPost.setEntity (new HttpXMLEntity (aDoc, eSoapVersion.getMimeType ()));
              }
              else
                throw new IllegalStateException ("Some test message should be selected :)");

      // re-instantiate if you want to see the request that is getting sent
      LOGGER.info (EntityUtils.toString (aPost.getEntity ()));

      final CloseableHttpResponse aHttpResponse = aClient.execute (aPost);

      LOGGER.info ("GET Response Status:: " + aHttpResponse.getStatusLine ().getStatusCode ());

      // print result
      LOGGER.info (EntityUtils.toString (aHttpResponse.getEntity ()));
    }
    catch (final Exception e)
    {
      LOGGER.error ("Error occurred while sending SOAP Request to Server", e);
    }
  }
}
