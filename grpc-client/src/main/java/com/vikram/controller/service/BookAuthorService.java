package com.vikram.controller.service;

import com.google.protobuf.Descriptors;
import com.vikram.TempDB;
import com.vikram.grpc.Author;
import com.vikram.grpc.Book;
import com.vikram.grpc.BookAuthorServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
public class BookAuthorService {

    @GrpcClient("grpc-service-channel")
    BookAuthorServiceGrpc.BookAuthorServiceBlockingStub syncClient;

    @GrpcClient("grpc-service-channel")
    BookAuthorServiceGrpc.BookAuthorServiceStub asyncClient;

    public Map<Descriptors.FieldDescriptor, Object> getAuthor(int authorId){
        Author authorRequest = Author.newBuilder().setAuthorId(authorId).build();
        Author authorResponse = syncClient.getAuthor(authorRequest);
        return authorResponse.getAllFields();
    }

    public List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthor(int authorId) throws InterruptedException {
        // StreamObserver will run on a diff thread
        // So We need to use CountDownLatch
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Map<Descriptors.FieldDescriptor, Object>> response = new ArrayList<>();
        Author authorRequest = Author.newBuilder().setAuthorId(authorId).build();
        asyncClient.getBooksByAuthor(authorRequest, new StreamObserver<Book>() {
            @Override
            public void onNext(Book book) {
                // On getting a book from server
                response.add(book.getAllFields());
            }

            @Override
            public void onError(Throwable throwable) {
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                countDownLatch.countDown();
            }
        });
        boolean await = countDownLatch.await(1, TimeUnit.MINUTES);
        return await ? response : Collections.emptyList();
    }

    public  Map<String, Map<Descriptors.FieldDescriptor, Object> > getExpensiveBook() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Map<String, Map<Descriptors.FieldDescriptor, Object> > response = new HashMap<>();
        StreamObserver<Book> bookStreamObserver = asyncClient.getExpensiveBook(new StreamObserver<Book>() {
            // Call back method when server done
            @Override
            public void onNext(Book book) {
                response.put("ExpensiveBook",book.getAllFields());
            }

            @Override
            public void onError(Throwable throwable) {
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                countDownLatch.countDown();
            }
        });

        // For each book send it to server using onNext
        // Server's on next method will get called for each book
        TempDB.getBooksFromTempDb().forEach(bookStreamObserver::onNext);
        bookStreamObserver.onCompleted();
        boolean await = countDownLatch.await(1, TimeUnit.MINUTES);
        return await ? response : Collections.emptyMap();
    }

    public List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthorGender(String gender) throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Map<Descriptors.FieldDescriptor, Object>> response = new ArrayList<>();
        StreamObserver<Book> responseObserver = asyncClient.getBookByAuthorGender(new StreamObserver<Book>() {
            @Override
            public void onNext(Book book) {
                // When server streams back this client's on next called
                response.add(book.getAllFields());
            }

            @Override
            public void onError(Throwable throwable) {
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                countDownLatch.countDown();
            }
        });


        TempDB.getAuthorsFromTempDb()
                .stream()
                .filter(author -> author.getGender().equals(gender))
                .forEach(author -> responseObserver.onNext(Book.newBuilder().setAuthorId(author.getAuthorId()).build()));
        responseObserver.onCompleted();
        boolean await = countDownLatch.await(1, TimeUnit.MINUTES);
        return await ? response : Collections.emptyList();
    }
}


