/*
 * MIT License
 *
 * Copyright (c) 2018, Miguel Gamboa (gamboa.pt)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.javaync.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

import static java.nio.ByteBuffer.wrap;
import static java.nio.channels.AsynchronousFileChannel.open;

/**
 * Asynchronous non-blocking write operations with a CompletableFuture based API.
 * These operations use an underlying AsynchronousFileChannel.
 * All methods are asynchronous including the close() which chains a continuation
 * on last resulting write CompletableFuture to close the AsyncFileChannel on completion.
 * All write methods return a CompletableFuture of an integer with the final file index
 * after the completion of corresponding write operation.
 */
public class AsyncFileWriter implements AutoCloseable{

    final AsynchronousFileChannel asyncFile;
    /**
     * File position after last write operation completion.
     */
    CompletableFuture<Integer> pos = CompletableFuture.completedFuture(0);

    public AsyncFileWriter(AsynchronousFileChannel asyncFile) {
        this.asyncFile = asyncFile;
    }

    public AsyncFileWriter(Path file, StandardOpenOption...options) {
        try {
            asyncFile = open(file, options);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public AsyncFileWriter(Path file) {
        this(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public AsyncFileWriter(String path, StandardOpenOption...options) {
        this(Paths.get(path), options);
    }

    public AsyncFileWriter(String path) {
        this(Paths.get(path), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /**
     * Writes the given String appended with a newline separator
     * and returns a CompletableFuture of the final file index
     * after the completion of the corresponding write operation.
     */
    public CompletableFuture<Integer> writeLine(String str) {
        return write(str + System.lineSeparator());
    }

    /**
     * Writes the given String and returns a CompletableFuture of
     * the final file index after the completion of the corresponding
     * write operation.
     */
    public CompletableFuture<Integer> write(String str) {
        return write(str.getBytes());
    }

    /**
     * Writes the given byte array and returns a CompletableFuture of
     * the final file index after the completion of the corresponding
     * write operation.
     */
    public CompletableFuture<Integer> write(byte[] bytes) {
        return write(wrap(bytes));
    }

    /**
     * Writes the given byte buffer and returns a CompletableFuture of
     * the final file index after the completion of the corresponding
     * write operation.
     */
    public CompletableFuture<Integer> write(ByteBuffer bytes) {
        /**
         * Wee need to update pos field to keep track.
         * The pos field is used on close() method, which chains
         * a continuation to close the AsyncFileChannel.
         */
        pos = pos.thenCompose(index -> {
            CompletableFuture<Integer> size = write(asyncFile, bytes, index);
            return size.thenApply(length -> length + index);
        });
        return pos;
    }


    static CompletableFuture<Integer> write(
            AsynchronousFileChannel asyncFile,
            ByteBuffer buf,
            int position)
    {
        CompletableFuture<Integer> promise = new CompletableFuture<>();
        asyncFile.write(buf, position, null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                promise.complete(result);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                promise.completeExceptionally(exc);
            }
        });
        return promise;
    }

    /**
     * Asynchronous close operation.
     * Chains a continuation on CompletableFuture resulting from last write operation,
     * which closes the AsyncFileChannel on completion.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        if(asyncFile != null) {
            pos.whenComplete((res, ex) ->
                    closeAfc(asyncFile)
            );
        }
    }

    private static void closeAfc(AsynchronousFileChannel asyncFile) {
        try {
            asyncFile.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
