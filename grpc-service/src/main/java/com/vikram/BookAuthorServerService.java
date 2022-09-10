package com.vikram;

import com.vikram.grpc.Author;
import com.vikram.grpc.Book;
import com.vikram.grpc.BookAuthorServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;

@GrpcService
public class BookAuthorServerService extends BookAuthorServiceGrpc.BookAuthorServiceImplBase {
    @Override
    public void getAuthor(Author request, StreamObserver<Author> responseObserver) {
        TempDB.getAuthorsFromTempDb().stream()
                .filter(
                        author -> author.getAuthorId()
                                == request.getAuthorId())
                .findFirst()
                .ifPresent(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    @Override
    public void getBooksByAuthor(Author request, StreamObserver<Book> responseObserver) {
        TempDB.getBooksFromTempDb()
                .stream()
                .filter(book -> book.getAuthorId() == request.getAuthorId())
                .forEach(responseObserver::onNext);
                // Whenever any book comes to forEach it streams it to the client in real time
                // We are sending a list to the client

        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<Book> getExpensiveBook(StreamObserver<Book> responseObserver) {
        // Client will send multiple (messages) books to server
        // Now server has to implement StreamObserver
        return new StreamObserver<Book>() {
            Book expensiveBook = null;
            float priceTrack = 0;
            // On each book from client onNext will be called
            @Override
            public void onNext(Book book) {
                if(book.getPrice() > priceTrack) {
                    priceTrack = book.getPrice();
                    expensiveBook = book;
                }
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                // When all the books has been consumed by onNext
                // Send expensive book back
                responseObserver.onNext(expensiveBook);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<Book> getBookByAuthorGender(StreamObserver<Book> responseObserver) {
         return new StreamObserver<>() {
             final List<Book> bookList = new ArrayList<>();

             @Override
             // When client sends  a book this invoked
             public void onNext(Book book) {
                 TempDB.getBooksFromTempDb()
                         .stream()
                         .filter(bookFromDB -> bookFromDB.getAuthorId() == book.getAuthorId())
                         .forEach(bookList::add);
             }

             @Override
             public void onError(Throwable throwable) {
                 responseObserver.onError(throwable);
             }

             @Override
             public void onCompleted() {
                 // Stream a response back to client
                 bookList.forEach(responseObserver::onNext);
                 responseObserver.onCompleted();
             }
         };
    }
}
