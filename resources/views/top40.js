function(doc) {
  var TOP_SIDS = [2, 4, 9, 11, 12, 13, 16, 18, 20, 22, 24, 26, 28, 30, 37, 39,
      40, 41, 47, 59, 78, 79, 97, 114, 117, 127, 156, 162, 163, 172, 226, 302,
      326, 368, 380, 400, 454, 456, 507, 508]
  if(doc.sid in TOP_SIDS) {
    emit(doc.sid, doc);
  }
}
