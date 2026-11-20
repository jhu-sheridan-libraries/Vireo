var dataSubmissionRepo1 = [
    dataSubmission1,
    dataSubmission2,
    dataSubmission3
];

var dataSubmissionRepo2 = [
    dataSubmission3,
    dataSubmission2,
    dataSubmission1
];

var dataSubmissionRepo3 = [
    dataSubmission4,
    dataSubmission5,
    dataSubmission6
];

angular.module("mock.submissionRepo", []).service("SubmissionRepo", function($q) {
    var repo = mockRepo("SubmissionRepo", $q, mockSubmission, dataSubmissionRepo1);

    repo.batchAssignTo = function (assignee) {
        var payload = {};
        return payloadPromise($q.defer(), payload);
    };

    repo.batchExport = function (packager, filterId) {
        var payload = {};
        return payloadPromise($q.defer(), payload);
    };

    // WsApi.fetch resolves REST calls as { body: <JSON string> }; these mocks use that shape.
    repo.batchPublish = function (depositLocation) {
        return $q.resolve({
            body: angular.toJson({ meta: { status: "SUCCESS" }, payload: {} })
        });
    };

    // The server's ERROR response when some deposits fail.
    repo.batchPublishError = function (message) {
        return $q.resolve({
            body: angular.toJson({
                meta: {
                    status: "ERROR",
                    type: "ERROR",
                    message: message || "Batch publish failed"
                },
                payload: {}
            })
        });
    };

    // RestApi.handleError's result for an HTTP error (for example a gateway timeout): no message.
    repo.batchPublishTransportError = function () {
        return $q.resolve({
            body: angular.toJson({ meta: { status: "ERROR" }, payload: {} })
        });
    };

    repo.batchUpdateStatus = function (submissionStatus) {
        var payload = {};
        return payloadPromise($q.defer(), payload);
    };

    repo.fetchSubmissionById = function (id) {
        var payload = repo.findById(id);

        if (payload === undefined) {
            // FIXME: callers, such as AdminSubmissionViewController, are not handling the reject case.
            // return a default payload as a work-around.
            payload = dataSubmissionRepo1[0];
            //return rejectPromise($q.defer());
        }

        return valuePromise($q.defer(), repo.mockModel(payload));
    };

    repo.query = function (columns, page, size) {
        var payload = {};
        return payloadPromise($q.defer(), payload);
    };

    return repo;
});
