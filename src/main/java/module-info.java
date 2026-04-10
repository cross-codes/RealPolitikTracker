module com.github.evermore {
    requires com.google.protobuf;
    requires kafka.clients;
    requires javafx.controls;
    requires io.grpc;
    requires io.grpc.protobuf;
    requires io.grpc.stub;
    requires com.google.common;
    exports com.github.evermore;
    exports com.github.evermore.proto;
    requires ortools.java;
}
