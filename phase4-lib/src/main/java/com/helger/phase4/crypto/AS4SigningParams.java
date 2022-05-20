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
package com.helger.phase4.crypto;

import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.lang.ICloneable;
import com.helger.commons.string.ToStringGenerator;
import com.helger.phase4.model.pmode.leg.PModeLegSecurity;

/**
 * AS4 signing parameters
 *
 * @author Philip Helger
 * @since 0.9.0
 */
@NotThreadSafe
public class AS4SigningParams implements Serializable, ICloneable <AS4SigningParams>
{
  public static final ECryptoKeyIdentifierType DEFAULT_KEY_IDENTIFIER_TYPE = ECryptoKeyIdentifierType.BST_DIRECT_REFERENCE;

  // The key identifier type to use
  private ECryptoKeyIdentifierType m_eKeyIdentifierType = DEFAULT_KEY_IDENTIFIER_TYPE;
  private ECryptoAlgorithmSign m_eAlgorithmSign;
  private ECryptoAlgorithmSignDigest m_eAlgorithmSignDigest;
  private ECryptoAlgorithmC14N m_eAlgorithmC14N = ECryptoAlgorithmC14N.C14N_ALGORITHM_DEFAULT;

  public AS4SigningParams ()
  {}

  /**
   * @return <code>true</code> if signing is enabled, <code>false</code> if not
   */
  public boolean isSigningEnabled ()
  {
    return m_eAlgorithmSign != null && m_eAlgorithmSignDigest != null;
  }

  /**
   * @return The key identifier type. May not be <code>null</code>.
   * @since 0.11.0
   */
  @Nonnull
  public final ECryptoKeyIdentifierType getKeyIdentifierType ()
  {
    return m_eKeyIdentifierType;
  }

  /**
   * Set the key identifier type to use. That defines how the information about
   * the signing certificate is transmitted.
   *
   * @param eKeyIdentifierType
   *        The key identifier type to use. May not be <code>null</code>.
   * @return this for chaining
   * @since 0.11.0
   */
  @Nonnull
  public final AS4SigningParams setKeyIdentifierType (@Nonnull final ECryptoKeyIdentifierType eKeyIdentifierType)
  {
    ValueEnforcer.notNull (eKeyIdentifierType, "KeyIdentifierType");
    m_eKeyIdentifierType = eKeyIdentifierType;
    return this;
  }

  /**
   * @return The signing algorithm to use. May be <code>null</code>.
   */
  @Nullable
  public final ECryptoAlgorithmSign getAlgorithmSign ()
  {
    return m_eAlgorithmSign;
  }

  /**
   * A signing algorithm can be set. <br>
   * MANDATORY if you want to use sign.<br>
   * Also @see {@link #setAlgorithmSignDigest(ECryptoAlgorithmSignDigest)}
   *
   * @param eAlgorithmSign
   *        the signing algorithm that should be set
   * @return this for chaining
   */
  @Nonnull
  public final AS4SigningParams setAlgorithmSign (@Nullable final ECryptoAlgorithmSign eAlgorithmSign)
  {
    m_eAlgorithmSign = eAlgorithmSign;
    return this;
  }

  /**
   * @return The signing digest algorithm to use. May be <code>null</code>.
   */
  @Nullable
  public final ECryptoAlgorithmSignDigest getAlgorithmSignDigest ()
  {
    return m_eAlgorithmSignDigest;
  }

  /**
   * A signing digest algorithm can be set. <br>
   * MANDATORY if you want to use sign.<br>
   * Also @see {@link #setAlgorithmSign(ECryptoAlgorithmSign)}
   *
   * @param eAlgorithmSignDigest
   *        the signing digest algorithm that should be set
   * @return this for chaining
   */
  @Nonnull
  public final AS4SigningParams setAlgorithmSignDigest (@Nullable final ECryptoAlgorithmSignDigest eAlgorithmSignDigest)
  {
    m_eAlgorithmSignDigest = eAlgorithmSignDigest;
    return this;
  }

  /**
   * @return The canonicalization algorithm to use. Never <code>null</code>.
   * @since 0.10.6
   */
  @Nonnull
  public final ECryptoAlgorithmC14N getAlgorithmC14N ()
  {
    return m_eAlgorithmC14N;
  }

  /**
   * Set the canonicalization algorithm to be used. By default "Exclusive
   * without comments" is used as suggested by the WS Security SOAP Message
   * Security Version 1.1.1 spec, chapter 8.1.<br>
   * Source:
   * http://docs.oasis-open.org/wss-m/wss/v1.1.1/wss-SOAPMessageSecurity-v1.1.1.doc
   *
   * @param eAlgorithmC14N
   *        the canonicalization algorithm that should be set. May not be
   *        <code>null</code>.
   * @return this for chaining
   * @since 0.10.6
   */
  @Nonnull
  public final AS4SigningParams setAlgorithmC14N (@Nonnull final ECryptoAlgorithmC14N eAlgorithmC14N)
  {
    ValueEnforcer.notNull (eAlgorithmC14N, "AlgorithmC14N");
    m_eAlgorithmC14N = eAlgorithmC14N;
    return this;
  }

  /**
   * This method calls {@link #setAlgorithmSign(ECryptoAlgorithmSign)} and
   * {@link #setAlgorithmSignDigest(ECryptoAlgorithmSignDigest)} based on the
   * PMode parameters. If the PMode parameter is <code>null</code> both values
   * will be set to <code>null</code>.
   *
   * @param aSecurity
   *        The PMode security stuff to use. May be <code>null</code>.
   * @return this for chaining
   */
  @Nonnull
  public final AS4SigningParams setFromPMode (@Nullable final PModeLegSecurity aSecurity)
  {
    // Note: The canonicalization algorithm is not part of the PMode!
    if (aSecurity == null)
    {
      setAlgorithmSign (null);
      setAlgorithmSignDigest (null);
    }
    else
    {
      setAlgorithmSign (aSecurity.getX509SignatureAlgorithm ());
      setAlgorithmSignDigest (aSecurity.getX509SignatureHashFunction ());
    }
    return this;
  }

  @Nonnull
  @ReturnsMutableCopy
  public AS4SigningParams getClone ()
  {
    return new AS4SigningParams ().setKeyIdentifierType (m_eKeyIdentifierType)
                                  .setAlgorithmSign (m_eAlgorithmSign)
                                  .setAlgorithmSignDigest (m_eAlgorithmSignDigest)
                                  .setAlgorithmC14N (m_eAlgorithmC14N);
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (null).append ("KeyIdentifierType", m_eKeyIdentifierType)
                                       .append ("AlgorithmSign", m_eAlgorithmSign)
                                       .append ("AlgorithmSignDigest", m_eAlgorithmSignDigest)
                                       .append ("AlgorithmC14N", m_eAlgorithmC14N)
                                       .getToString ();
  }

  /**
   * @return A non-<code>null</code> {@link AS4SigningParams} object with
   *         default values assigned.
   * @see #setAlgorithmSign(ECryptoAlgorithmSign)
   * @see #setAlgorithmSignDigest(ECryptoAlgorithmSignDigest)
   * @see #setAlgorithmC14N(ECryptoAlgorithmC14N)
   */
  @Nonnull
  @ReturnsMutableObject
  public static AS4SigningParams createDefault ()
  {
    return new AS4SigningParams ().setAlgorithmSign (ECryptoAlgorithmSign.SIGN_ALGORITHM_DEFAULT)
                                  .setAlgorithmSignDigest (ECryptoAlgorithmSignDigest.SIGN_DIGEST_ALGORITHM_DEFAULT)
                                  .setAlgorithmC14N (ECryptoAlgorithmC14N.C14N_ALGORITHM_DEFAULT);
  }
}
