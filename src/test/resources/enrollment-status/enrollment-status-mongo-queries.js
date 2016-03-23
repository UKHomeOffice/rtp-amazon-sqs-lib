// find given case and return GE related checks
db.submissions.find({ 'registeredTravellerNumber' : 'GEABC2345'}, {'checks.geApplicationDetails': 1, 'checks.geApplicationStatus': 1})

// find all cases that have application accepted in GES and return GE related checks
db.submissions.find({ 'checks.geApplicationStatus.status' : 'CHECK_ACCEPTED'}, {'checks.geApplicationDetails': 1, 'checks.geApplicationStatus': 1, 'checks.geEnrollmentStatus':1})

// find all cases that have application accepted in GES
db.submissions.find({ 'checks.geApplicationStatus.status' : 'CHECK_ACCEPTED'})

// update enrollment status
db.submissions.update({ _id: ObjectId("56ba2642d4c6203cbe28fafe") }, { $set: { 'checks.geEnrollmentStatus.geEnrollmentStatus': 'REVOKED'}})
db.submissions.update({ _id: ObjectId("56ba2642d4c6203cbe28fafe") }, { $set: { 'checks.geEnrollmentStatus.geEnrollmentStatus': 'DENIED'}})
db.submissions.update({ _id: ObjectId("56ba2642d4c6203cbe28fafe") }, { $set: { 'checks.geEnrollmentStatus.geEnrollmentStatus': 'APPROVED', 'checks.geEnrollmentStatus.enrollmentExpiryDate': ISODate("2020-03-13T00:00:00Z")}})
db.submissions.update({ _id: ObjectId("56ba2642d4c6203cbe28fafe") }, { $set: { 'checks.geEnrollmentStatus.geEnrollmentStatus': 'APPROVED', 'checks.geEnrollmentStatus.enrollmentExpiryDate': ISODate("2010-03-13T00:00:00Z")}})
db.submissions.update({ _id: ObjectId("56ba2642d4c6203cbe28fafe") }, { $set: { 'checks.geEnrollmentStatus.geEnrollmentStatus': 'EXPIRED', 'checks.geEnrollmentStatus.enrollmentExpiryDate': ISODate("2010-03-13T00:00:00Z")}})