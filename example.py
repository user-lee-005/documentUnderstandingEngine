from gradient_tree import GradientTree

X = [
    [1, 2],
    [2, 3],
    [3, 4],
    [4, 5]
]

y = [10, 20, 30, 40]

model = GradientTree()

model.train(X, y)

result = model.get_result([[5, 6]])

print(result)