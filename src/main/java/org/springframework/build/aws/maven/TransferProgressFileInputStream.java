/*
 * Copyright 2010-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.aws.maven;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

final class TransferProgressFileInputStream extends InputStream {

    private final InputStream delegate;
    private final WagonS3TransferListenerAdapter transferProgress;

    TransferProgressFileInputStream(InputStream delegate, WagonS3TransferListenerAdapter transferProgress) throws FileNotFoundException {
        this.delegate = delegate;
        this.transferProgress = transferProgress;
    }

    @Override
    public int read() throws IOException {
        int b = delegate.read();
        this.transferProgress.notify(new byte[]{(byte) b}, 1);
        return b;
    }

    @Override
    public int read(byte b[]) throws IOException {
        int count = delegate.read(b);
        this.transferProgress.notify(b, b.length);
        return count;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        int count = delegate.read(b, off, len);
        if (off == 0) {
            this.transferProgress.notify(b, len);
        } else {
            byte[] bytes = new byte[len];
            System.arraycopy(b, off, bytes, 0, len);
            this.transferProgress.notify(bytes, len);
        }
        return count;
    }
}
