/*
 * /////////////////////////////////////////////////////////////////////////////
 *
 * Copyright (c) 2025 Indra Sistemas, S.A. All Rights Reserved.
 * http://www.indracompany.com/
 *
 * The contents of this file are owned by Indra Sistemas, S.A. copyright holder.
 * This file can only be copied, distributed and used all or in part with the
 * written permission of Indra Sistemas, S.A, or in accordance with the terms and
 * conditions laid down in the agreement / contract under which supplied.
 *
 * /////////////////////////////////////////////////////////////////////////////
 */
package com.indra.minsait.dvsmart.indexing.infrastructure.sftp;

import lombok.RequiredArgsConstructor;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import com.indra.minsait.dvsmart.indexing.infrastructure.config.SftpConfigProperties;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 12-12-2025 at 13:05:17
 * File: SftpSessionFactoryConfig.java
 */

@Configuration
@RequiredArgsConstructor
public class SftpSessionFactoryConfig {

    private final SftpConfigProperties props;

    @Bean(name = "sftpOriginSessionFactory")
    SessionFactory<SftpClient.DirEntry> sftpOriginSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(props.getOrigin().getHost());
        factory.setPort(props.getOrigin().getPort());
        factory.setUser(props.getOrigin().getUser());
        factory.setPassword(props.getOrigin().getPassword());
        factory.setTimeout(props.getOrigin().getTimeout());
        factory.setAllowUnknownKeys(true);

        CachingSessionFactory<SftpClient.DirEntry> cachingFactory = new CachingSessionFactory<>(factory);
        cachingFactory.setPoolSize(props.getOrigin().getPool().getSize());

        return cachingFactory;
    }

    @Bean(name = "sftpOriginTemplate")
    SftpRemoteFileTemplate sftpOriginTemplate() {
        return new SftpRemoteFileTemplate(sftpOriginSessionFactory());
    }

}