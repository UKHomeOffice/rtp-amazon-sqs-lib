// These are mongo queries that can be used for testing unmatched together with the Unmatched.json.postman_collection

// find the submission with given UK Access Code
db.submissions.find({ 'registeredTravellerNumber' : 'GEABC2345'})

// find the submission with the fixed passport number
db.submissions.find({ 'latestApplication.passportDetails.passportNumber':'ABC1234567890' })

// update the submission for the fixed passport number to have the correct UK Access Code
db.submissions.update({ 'latestApplication.passportDetails.passportNumber':'ABC1234567890' }, { $set: { registeredTravellerNumber: 'GEABC2345'}})

// delete the fixed case
db.submissions.remove({registeredTravellerNumber : 'GEABC2345'}, 1)

// remove all records from the GE unmatched collection
db.unmatchedGEApplicationDetails.remove({})