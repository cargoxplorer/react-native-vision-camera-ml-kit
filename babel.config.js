module.exports = {
  // Only used for Jest tests - builder-bob handles library building
  env: {
    test: {
      presets: ['module:@react-native/babel-preset'],
    },
  },
};
